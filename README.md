# MOA (모아)

### 이동하는 순간에도, 여행의 다음 장면을 발견하다.

**MOA**는 목적지로 이동하는 동안 여행자의 취향과 여행 상황을 고려해 주변 장소를 제안하는 **Pleos Connect 기반 차량용 여행 동반자 앱**입니다. 단순한 목적지 검색에 그치지 않고, 이동 중 장소 추천, 여행 정보 설명, 음성 안내를 하나의 흐름으로 연결하는 것을 목표로 합니다.

> **프로젝트 성격**: 2026 현대자동차그룹 차량용 앱 개발 공모전 출품 프로젝트 · 개발 및 시연용 프로토타입

## 1. 핵심 사용자 시나리오

1. **여행 준비**: 여행자 프로필과 여행 조건을 선택합니다.
2. **목적지 설정**: 첫 목적지를 선택하고 내비게이션 안내를 시작합니다.
3. **이동 중 추천**: 현재 여행 맥락에 맞는 주변 장소를 탐색하고 후보를 제안합니다.
4. **추천 장소 선택**: 관심 있는 장소를 선택해 다음 방문 장소로 반영합니다.
5. **이야기 듣기**: 여행지의 역사·문화·음식 관련 설명을 확인하고 음성으로 들을 수 있도록 구성합니다.

## 2. 주요 구성 요소

| 영역 | 설명 |
| --- | --- |
| 여행 관리 | 프로필·여행 조건·목적지 및 여행 상태를 관리하는 화면과 흐름 |
| 맞춤 장소 추천 | Kakao Local API로 장소 후보를 조회하고 여행 맥락에 따라 점수화 |
| 추천 점수 모델 | 프로필, 여행 목적, 경험, 기분, 접근성, 다양성 등의 항목을 반영하는 `MoaScoreBreakdown` 구조 |
| 목적지 중복 방지 | 현재 선택한 목적지나 거절한 후보가 반복 추천되지 않도록 필터링 |
| 내비게이션 연계 | Pleos Connect의 내비게이션 연계 기능을 활용한 주행 맥락의 UX |
| 여행 도슨트 | 관광 정보와 LLM을 결합해 짧은 여행 설명을 생성하는 흐름 |
| 음성 기능 | 음성 인식·명령 처리 UI와 Pleos TTS 안내를 연계하는 구성 |
| 차량용 화면 | 내비게이션과 앱을 함께 사용하는 분할 화면 시나리오 고려 |

**구현·검증 범위 안내:** 위 표는 프로젝트의 주요 설계·코드 구성 요소를 설명합니다. 모든 기능이 실제 차량에서 검증되었거나 상용 수준으로 완성되었다는 의미는 아닙니다. 특히 지속적인 백그라운드 추천, 음성 인식의 환경별 동작, 주행 중 사용성은 별도 통합 검증이 필요합니다.

## 3. 기술 스택

| 구분 | 기술 |
| --- | --- |
| Android 앱 | Kotlin, Jetpack Compose |
| 차량 플랫폼 | Pleos Connect / Android Automotive 환경 |
| 내비게이션 연계 | Pleos NaviHelper 기반 연동 |
| 음성 출력 | Pleos TTS |
| 장소 탐색 | Kakao Local API |
| 관광 데이터 | 한국관광공사 TourAPI |
| 설명 생성·음성 처리 | OpenAI API를 사용하는 Python 백엔드 구성 |
| 백엔드 | Python, FastAPI |
| 버전 관리 | Git, GitHub |

## 4. 저장소 구조

아래는 개발 과정에서 확인된 주요 디렉터리와 파일을 요약한 것입니다. 실제 저장소에는 추가 모듈 및 리소스가 포함될 수 있습니다.

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/main/java/com/example/app/
│       ├── data/
│       │   └── KakaoLocalRepository.kt
│       ├── ui/voice/
│       │   └── MoaVoiceManager.kt
│       └── vehicle/
│           └── MoaDriverDoorCard.kt
├── MOA_Docent_FastAPI_Starter/
│   ├── app.py
│   ├── tourapi_client.py
│   ├── tourapi_retry.py
│   └── README_ko.md
├── gradle.properties
└── README.md
```

## 5. 시작하기

### 준비 사항

- Android Studio와 해당 프로젝트에 맞는 Android SDK / JDK
- Pleos Connect 개발·실행 환경 (실제 차량 서비스에 의존하는 기능은 일반 Android 에뮬레이터에서 제한될 수 있음)
- Kakao Local API 키
- 백엔드 기능을 사용하는 경우 OpenAI API 키 및 TourAPI 키

### Android 앱 설정

1. 저장소를 클론합니다.

   ```powershell
   git clone https://github.com/Etik1vvb/APP.git
   cd APP
   ```

2. Android Studio로 저장소 루트를 열고 Gradle 동기화를 진행합니다.
3. 프로젝트 루트의 **`local.properties`**에 카카오 REST API 키를 설정합니다. 기존 파일에 SDK 경로가 있다면 유지하세요.

   ```properties
   KAKAO_REST_API_KEY=YOUR_KAKAO_REST_API_KEY
   ```

4. PowerShell에서 디버그 APK를 빌드합니다.

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

5. Pleos Connect 개발용 에뮬레이터 또는 지원 환경에서 앱을 실행합니다.

> `local.properties`는 개발 환경마다 다른 **로컬 파일**입니다. 실제 API 키가 포함된 이 파일을 Git에 올리지 마세요.

### FastAPI 백엔드 설정

백엔드는 `MOA_Docent_FastAPI_Starter/`에 있습니다.

1. 해당 폴더로 이동해 Python 가상 환경을 만듭니다.

   ```powershell
   cd MOA_Docent_FastAPI_Starter
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1
   ```

2. 필요한 패키지를 설치합니다. `requirements.txt`가 존재하는 경우 다음 명령을 사용할 수 있습니다.

   ```powershell
   pip install -r requirements.txt
   ```

3. 백엔드 폴더에 `.env` 파일을 생성합니다.

   ```dotenv
   OPENAI_API_KEY=YOUR_OPENAI_API_KEY
   TOUR_API_KEY=YOUR_TOUR_API_KEY
   ```

4. `app.py`의 FastAPI 앱을 실행합니다.

   ```powershell
   python -m uvicorn app:app --host 127.0.0.1 --port 8000
   ```

기존에 8000번 포트를 사용 중인 프로세스가 있다면 다른 포트로 실행해야 합니다. API 구성과 세부 실행 조건은 `MOA_Docent_FastAPI_Starter/README_ko.md`도 참고하세요.

> **연결 주의:** Android 에뮬레이터에서는 `127.0.0.1`이 개발 PC가 아니라 에뮬레이터 자체를 가리킬 수 있습니다. 사용하는 에뮬레이터·네트워크 환경에 맞춰 백엔드 접속 주소를 설정해야 합니다.

## 6. 추천 방식 개요

MOA는 장소 후보를 가져온 후 여행자 프로필과 여행 맥락에 따라 우선순위를 계산하도록 설계되었습니다.

```text
사용자 프로필 및 여행 조건
           ↓
    주변 장소 후보 탐색
           ↓
  중복·거절 장소 후보 필터링
           ↓
  다중 요소 기반 추천 점수 산정
           ↓
     추천 후보 표시
           ↓
  사용자 선택 및 목적지 반영
```

설계에 사용한 주요 평가 축은 **프로필, 목적, 경험, 기분, 접근성, 다양성**입니다. 점수 임계값, 우회 거리 및 개인화 가중치는 테스트 조건과 구현 단계에 따라 조정될 수 있습니다.

## 7. 현재 단계와 검증 과제

이 저장소는 **시연용 프로토타입**입니다. 실제 차량용 서비스에 필요한 통합 테스트 및 정책 검토는 별도 작업으로 남아 있습니다.

- [x] Kotlin / Jetpack Compose 앱 프로젝트 구성
- [x] Kakao Local 연계 코드 및 추천 점수 구조 구성
- [x] 음성 기능 및 Pleos TTS 관련 코드 통합
- [x] Python FastAPI 기반 관광 정보·LLM 연계 코드 구성
- [ ] 모든 핵심 시나리오의 실제 주행 환경 검증
- [ ] 백그라운드 추천 및 음성 인식의 환경별 안정성 최종 검증
- [ ] API 요청 실패·네트워크 단절·권한 거부 상황의 종합 테스트
- [ ] 최종 시연 영상 및 스크린샷 등록

> 체크된 항목은 관련 코드 구성·개발 작업을 뜻하며, 전체 기능의 품질 보증이나 실차 검증 완료를 뜻하지 않습니다.

## 8. 보안 및 API 키 관리

- 실제 API 키는 Git 커밋에 포함하지 않습니다.
- Python 백엔드는 `.env` 또는 별도 비밀정보 관리 수단을 통해 키를 전달받습니다.
- Android 앱은 로컬 설정의 `KAKAO_REST_API_KEY`를 빌드에 주입합니다.
- **주의:** `BuildConfig`에 주입된 키는 배포 APK에서 추출될 수 있으므로 안전한 비밀 저장소가 아닙니다. 서비스 운영 시 API 제공자의 제한 정책을 적용하거나 서버를 통해 요청을 중계해야 합니다.
- 저장소 공개 전 Git 커밋 기록을 Gitleaks로 점검했습니다. 검사 시점에 9개 커밋에서 탐지된 비밀키는 0건이었습니다. 이는 모든 종류의 노출 위험이 없다는 보증은 아닙니다.

## 9. 시연 자료

최종 시연 영상과 스크린샷은 별도 준비 후 여기에 추가할 예정입니다.

<!-- 예시: 실제 파일을 추가한 뒤 아래 항목을 수정하세요.
- 시연 영상: https://...
- 화면 캡처: docs/screenshots/...
-->

## 10. 프로젝트 정보

- **프로젝트명:** MOA (모아)
- **목적:** 차량 이동 중 개인화된 여행 추천 및 여행 정보 안내 경험 구현
- **참가 분야:** 2026 현대자동차그룹 차량용 앱 개발 공모전
- **저장소:** https://github.com/Etik1vvb/APP

---

본 프로젝트는 공모전용 프로토타입입니다. 실제 도로에서의 사용은 관련 안전 요구사항과 차량 플랫폼 정책을 준수해야 합니다.
