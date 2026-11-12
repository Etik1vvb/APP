# MOA Docent: Windows 로컬 중계 서버 (1차 시험용)

이 서버는 **Kakao Local이나 TourAPI를 아직 자동 조회하지 않습니다.** `factSnippets`에 전달한 사실 문장만 근거로 짧은 도슨트 안내를 생성하는 최소 버전입니다. 검증된 역사 사실을 자동 확인하는 기능은 다음 단계입니다.

## 1. API 키 준비

OpenAI Dashboard의 **API Keys** 메뉴에서 `MOA-Docent` 프로젝트 키를 새로 만드세요. **키를 ChatGPT나 GitHub에 붙여 넣지 마세요.**

## 2. 준비 (Windows PowerShell, Python 설치 필요)

압축을 풀고 해당 폴더에서 PowerShell을 엽니다.

```powershell
py --version
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
notepad .env
```

메모장에서 `.env` 파일의 `OPENAI_API_KEY=` 뒤를 발급한 키로 변경하고 저장하세요. 따옴표는 필요하지 않습니다. `.env`는 `.gitignore`에 등록되어 있으나, Git에 올리지 않았는지도 꼭 확인하세요.

## 3. 서버 시작

```powershell
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

브라우저에서 `http://127.0.0.1:8000/docs` 를 열어 `/api/docent/story`를 시험할 수 있습니다.

## 4. 첫 테스트 (다른 PowerShell 창)

```powershell
curl.exe -X POST "http://127.0.0.1:8000/api/docent/story" -H "Content-Type: application/json; charset=utf-8" --data-binary "@sample_request.json"
```

응답에는 `status: ready`, `storyText`, `model: gpt-6-luna` 등이 나타나야 합니다. 생성된 글이 실제 역사적 사실과 일치한다는 보증은 **아닙니다**. 이번 예시의 `sourceName`은 개발용입니다.

키 설정 여부 확인: `http://127.0.0.1:8000/health` (키 자체는 반환하지 않음)

## 흔한 오류

- `py`를 찾지 못하면 Python을 설치하거나 `python` 명령으로 대체하세요.
- `503 OPENAI_API_KEY is not configured`: `.env` 파일 경로와 API 키 확인 후 서버 재실행.
- `502 LLM returned HTTP 401`: 잘못된 API 키 또는 프로젝트 불일치 가능성.
- `502 LLM returned HTTP 429`: 프로젝트 한도·모델 제한 또는 요청 빈도를 확인.
- `502 LLM returned HTTP 404`: 모델 ID가 해당 프로젝트에서 사용 가능한지 확인.
- `insufficient_source`: `factSnippets`가 비어 있어 LLM을 호출하지 않은 정상 동작.

## 보안·범위

**로컬 테스트 전용**입니다. 포트는 `127.0.0.1`에만 바인딩되며 인증, 요청 제한, 속도 제한, HTTPS, 실 서비스 키 관리가 구현되지 않았습니다. **외부에 공개하지 마세요.** Android 에뮬레이터에서 접근하려면 환경에 따른 호스트 접속 방식과 인증을 별도로 마련해야 합니다. 이 단계에서 OpenAI 키를 Android 앱 코드에 넣으면 안 됩니다.

실서비스 통합에서는 **TourAPI 사실 검증, 서버 캐시, 인증, rate limiting, 요청 사용량 한도, 출처 대조, 취소와 재시도, Pleos TTS**를 추가해야 합니다.
