
import os
from urllib.parse import unquote

import requests
from dotenv import load_dotenv

load_dotenv()

key = os.getenv("TOUR_API_KEY", "").strip()

if not key:
    raise RuntimeError(".env에 TOUR_API_KEY가 없습니다.")

# 인코딩 키와 디코딩 키 모두 중복 인코딩 없이 처리
key = unquote(key)

url = "https://apis.data.go.kr/B551011/KorService2/searchKeyword2"

params = {
    "serviceKey": key,
    "numOfRows": 10,
    "pageNo": 1,
    "MobileOS": "ETC",
    "MobileApp": "AppTest",
    "_type": "json",
    "arrange": "C",
    "keyword": "경복궁",
}

response = requests.get(url, params=params, timeout=20)

print("HTTP 상태:", response.status_code)

try:
    data = response.json()
    header = data.get("response", {}).get("header", {})
    print("API 결과:", header)
    print("검색 결과:", data.get("response", {}).get("body", {}).get("items", {}))
except ValueError:
    print("응답:", response.text[:1200])
