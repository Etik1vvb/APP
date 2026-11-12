"""한국관광공사 TourAPI 4.0 KorService2 최소 연동.

- 사용자의 장소명/주소와 TourAPI 결과가 정확히 맞는 경우에만 개요를 사용한다.
- API 키/인증 URL을 로그에 남기지 않는다.
- 외부 요청 실패시 호출자가 일반 장소 안내로 돌아갈 수 있게 예외를 발생시킨다.
"""
from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache
from html import unescape
import logging
import os
import re
from urllib.parse import unquote

import requests

log = logging.getLogger("moa.tourapi")
BASE_URL = "https://apis.data.go.kr/B551011/KorService2"


@dataclass(frozen=True)
class TourFacts:
    title: str
    address: str
    overview: str
    content_id: str


class TourApiError(RuntimeError):
    """일시적인 외부 서비스 실패. URL/키를 포함하지 않는다."""


def _key() -> str:
    # data.go.kr에서 발급된 인코딩/디코딩 키 중 어느 쪽이든 1회만 인코딩되도록 한다.
    return unquote(os.getenv("TOUR_API_KEY", "").strip())


def _fetch(endpoint: str, *, service_key: str, **values: object) -> list[dict]:
    params = {
        "serviceKey": service_key,
        "MobileOS": "ETC",
        "MobileApp": "MOA",
        "_type": "json",
        "numOfRows": 20,
        "pageNo": 1,
        **values,
    }
    try:
        response = requests.get(
            f"{BASE_URL}/{endpoint}",
            params=params,
            headers={"Accept": "application/json"},
            timeout=(3, 7),
        )
        response.raise_for_status()
        raw = response.json()
    except (requests.RequestException, ValueError) as error:
        raise TourApiError(f"{endpoint} 요청 또는 JSON 응답 실패") from error

    if not isinstance(raw, dict):
        raise TourApiError("TourAPI 응답 형식 오류")
    envelope = raw.get("response", raw)
    if not isinstance(envelope, dict):
        raise TourApiError("TourAPI response 형식 오류")
    header = envelope.get("header") or {}
    code = str(header.get("resultCode", "")) if isinstance(header, dict) else ""
    if code not in ("0000", "0", "00"):
        # API 키나 원본 URL을 로깅하지 않는다.
        raise TourApiError(f"TourAPI {endpoint} 응답 코드: {code or '없음'}")
    body = envelope.get("body") or {}
    items = body.get("items") or {} if isinstance(body, dict) else {}
    if not isinstance(items, dict):
        return []
    rows = items.get("item") or []
    if isinstance(rows, dict):
        rows = [rows]
    if not isinstance(rows, list):
        return []
    return [row for row in rows if isinstance(row, dict)]


def _normalize_title(value: str) -> str:
    # 괄호로 표기된 별칭이나 공백 차이는 허용, 다른 장소명은 매칭하지 않는다.
    value = re.sub(r"\([^)]*\)|\[[^]]*\]", "", value or "")
    return re.sub(r"[^가-힣A-Za-z0-9]", "", value).casefold()


def _city(address: str) -> str:
    head = (address or "").split()
    if not head:
        return ""
    prefix = head[0]
    aliases = {
        "서울": ("서울", "서울특별시"), "부산": ("부산", "부산광역시"),
        "대구": ("대구", "대구광역시"), "인천": ("인천", "인천광역시"),
        "광주": ("광주", "광주광역시"), "대전": ("대전", "대전광역시"),
        "울산": ("울산", "울산광역시"), "세종": ("세종", "세종특별자치시"),
        "경기": ("경기", "경기도"), "강원": ("강원", "강원특별자치도", "강원도"),
        "충북": ("충북", "충청북도"), "충남": ("충남", "충청남도"),
        "전북": ("전북", "전북특별자치도", "전라북도"), "전남": ("전남", "전라남도"),
        "경북": ("경북", "경상북도"), "경남": ("경남", "경상남도"),
        "제주": ("제주", "제주특별자치도"),
    }
    for canonical, variants in aliases.items():
        if prefix in variants:
            return canonical
    return ""


def _district(address: str) -> str:
    for part in (address or "").split():
        if 2 <= len(part) <= 10 and part.endswith(("구", "군")):
            return part
    return ""


def _same_region(expected: str, actual: str) -> bool:
    if not expected:
        return True
    want_city, got_city = _city(expected), _city(actual)
    if want_city and got_city and want_city != got_city:
        return False
    want_district, got_district = _district(expected), _district(actual)
    if want_district and got_district and want_district != got_district:
        return False
    # 지역 정보가 충분히 구체적인데 상대방은 주소를 전혀 제공하지 않는 경우 제외.
    if (want_city or want_district) and not actual:
        return False
    return True


def _clean_overview(raw: str) -> str:
    value = unescape(str(raw or ""))
    value = re.sub(r"<[^>]*>", " ", value)
    value = re.sub(r"\s+", " ", value).strip()
    return value[:1100]


@lru_cache(maxsize=256)
def find_tour_facts(place_name: str, region: str) -> TourFacts | None:
    """일치하는 TourAPI 관광지 개요만 반환. 검색 실패는 None, 서비스 실패는 TourApiError."""
    key = _key()
    if not key:
        return None
    normalized = _normalize_title(place_name)
    if not normalized:
        return None

    found = _fetch("searchKeyword2", service_key=key, keyword=place_name[:60])
    matching = [
        item for item in found
        if _normalize_title(str(item.get("title", ""))) == normalized
        and _same_region(region, str(item.get("addr1", "")))
        and item.get("contentid")
    ]
    if len(matching) != 1:
        # 이름이 같더라도 여러 결과가 있으면 엉뚱한 장소를 소개하는 것보다 생략한다.
        return None
    item = matching[0]
    details = _fetch("detailCommon2", service_key=key, contentId=item["contentid"])
    if not details:
        return None
    overview = _clean_overview(details[0].get("overview", ""))
    if not overview:
        return None
    return TourFacts(
        title=str(item.get("title", place_name)),
        address=str(item.get("addr1", "")),
        overview=overview,
        content_id=str(item["contentid"]),
    )
