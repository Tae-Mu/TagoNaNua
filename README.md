# 같이타 정산 (TagoNaNua)

한 차로 이동한 여행에서 사용자가 입력한 **실제 결제 금액**(주유·주차·톨비·기타)을 합산해 동승자별 분담액을 계산하고 공유하는 차량 앱입니다. 정산 총액은 정산에 포함한 비용의 실제 입력 금액 합계이며, 주행거리·연비·유가로 추정한 유류비를 더하지 않습니다. 주유량(L)은 금액÷단가로 표시만 합니다.

## 현재 상태

결제액 방식(`payments-v1`)의 핵심 흐름이 구현되어 있습니다.

- 비용 등록·수정·삭제와 정산 포함/제외 선택(FUEL/PARKING/TOLL/OTHER)
- 운전자 포함/제외, 인원 1~8명, 나머지를 순서대로 1원씩 배분하는 정수 원 분담
- 정산 미리보기와 확정(제외 비용까지 복사한 스냅샷, 확정 후 수정 불가), 정산 요약 텍스트 QR
- 월별 내역과 상태 필터, 앱 재실행 시 진행 중 여행 복구
- 실수로 시작한 빈 여행 닫기: 구간·비용·기록 거리가 모두 없는 진행 중 여행만 확인 후 삭제하고 홈으로 이동(기록이 있거나 완료·확정된 여행은 삭제하지 않음)
- Room 스키마 v4. v1 추정 정산은 삭제하지 않고 "옛 추정 정산"으로 구분해 읽기 전용으로 표시합니다.

거리·연비 수동 입력 UI는 제거했습니다. 새 여행은 VehicleDataSource의 PLEOS 측정값으로 시작하며, 현재 SDK 어댑터가 없어 연결 대기 안내 후 시작을 보류합니다. 임의의 0km나 샘플값으로 대체하지 않습니다. 기존 수동 기록은 보존합니다. 실제 시동/구간 자동 수신은 아직 미구현입니다. QR은 정산 요약 텍스트일 뿐 송금·입금 확인이 아니며 암호화나 접근 제어도 아닙니다.

## 검증 상태

2026-11-09 전체 연결 작업에서 JUnit·Room/Compose 기기 테스트·Lint·태블릿 직접 QA를 실행했다. 최신 수치와 재현 시나리오는 [전체 연결 검수](.codex/docs/full-integration-qa-2026-11-09.md)를 따른다. 이전 버전의 결과를 현재 결과로 대신하지 않는다.

## 제한 사항

- 실제 차량 PARKED·시동·거리 신호 및 command_receipts 범용 멱등 테이블은 미구현입니다. 등록/확정은 기존 requestId로 중복 방지합니다.
- 목록 삭제는 여행·정산 목록에서 숨기는 방식입니다. 확정·legacy 원본의 영구 삭제는 제공하지 않습니다.
- 실제 차량 데이터·권한·시동 OFF 동작·휴대폰 QR 리더의 한글 표시는 확인하지 않았습니다.
- v1에서 올라온 여행의 날짜는 UTC 기준으로 보정했고(v1은 시간대를 저장하지 않음), 인원은 옛 정산의 인원 또는 1명입니다.
- 외부 서버, 로그인, 결제, 송금·입금 확인, 클라우드 동기화는 범위에 없습니다.

## 빌드 및 검증

조건: JDK 17, Android SDK(platform 35). Gradle은 저장소의 Wrapper(`./gradlew`, Gradle 8.9)를 사용합니다. 라이브러리 버전은 `gradle/libs.versions.toml`에서 관리합니다. 처음 설치하는 경우 [설치 및 실행 가이드](docs/SETUP.md)(Pleos 에뮬레이터 포함)를 따르세요.

```bash
./gradlew :app:assembleDebug              # debug 빌드 (새 구현에서 실행·성공)
./gradlew :app:testDebugUnitTest          # JUnit 단위 테스트 (최신 결과는 검수 문서 참고)
./gradlew :app:lintDebug                  # Android Lint (최신 결과는 검수 문서 참고)
./gradlew :app:assembleDebugAndroidTest   # 기기 테스트 APK 빌드 (최신 결과는 검수 문서 참고)
./gradlew :app:connectedDebugAndroidTest  # Room·마이그레이션·화면 테스트, 에뮬레이터/기기 필요 (최신 결과는 검수 문서 참고)
```

- Room 스키마 이력 `app/schemas/com.gatita.data.local.AppDatabase/1.json`~`4.json`은 `MigrationTest`가 사용하므로 버전 관리 대상입니다.
- DB 버전을 올릴 때는 `data/local/migration/Migrations.kt`에 마이그레이션을 추가합니다. 파괴적 fallback은 사용하지 않습니다.
- `minSdk`(29)와 패키지명(`com.gatita`)은 임시값입니다.

## 개발 구성

| 구분 | 기술 |
|---|---|
| 언어 | Kotlin |
| UI | Jetpack Compose |
| 로컬 데이터 저장 | Room / SQLite |
| 차량 연동 | Pleos Vehicle SDK (미연동) |
| 비동기 처리 | Coroutines / Flow |
| 코드 편집 | VS Code 또는 Android Studio |
| 실행 및 디버깅 | Android SDK, JDK, Pleos 에뮬레이터 |

Room을 유일한 DB로 사용합니다. 첫 버전은 정산 요약 텍스트를 QR로 공유하며, 별도 백엔드와 MySQL은 사용하지 않습니다.

## 프로젝트 구조

```text
app/
├── schemas/                         # Room 스키마 이력
└── src/
    ├── main/
    │   ├── java/com/gatita/
    │   │   ├── di/                  # 의존 객체 생성 및 연결
    │   │   ├── navigation/          # 화면 이동
    │   │   ├── core/
    │   │   │   ├── ui/component/    # 공통 UI
    │   │   │   ├── ui/theme/        # 색상과 글꼴
    │   │   │   └── qr/              # QR 생성
    │   │   ├── domain/
    │   │   │   ├── model/           # 도메인 모델
    │   │   │   ├── repository/      # 저장소 인터페이스
    │   │   │   ├── vehicle/         # 차량 데이터 인터페이스
    │   │   │   ├── calculator/      # 정산 계산 규칙
    │   │   │   └── usecase/         # 여행·비용·정산 처리
    │   │   ├── data/
    │   │   │   ├── local/entity/    # Room 테이블
    │   │   │   ├── local/dao/       # DB 조회 및 저장
    │   │   │   ├── local/migration/ # DB 구조 변경
    │   │   │   ├── mapper/          # DB 모델과 도메인 모델 변환
    │   │   │   ├── repository/      # 저장소 구현
    │   │   │   └── vehicle/         # 샘플 데이터 구현(Pleos 구현은 예정)
    │   │   └── feature/
    │   │       ├── home/            # 홈
    │   │       ├── trip/            # 진행 중 여행과 비용 목록
    │   │       ├── cost/            # 비용 입력
    │   │       ├── settlement/      # 정산 미리보기·확정
    │   │       ├── history/         # 과거 내역
    │   │       └── share/           # 결과·QR 공유
    │   └── res/
    │       ├── drawable/           # 이미지와 아이콘
    │       └── values/             # 문자열 등 리소스
    ├── test/java/com/gatita/        # 단위 테스트(현재 결제액 방식과 legacy 회귀 검증)
    └── androidTest/java/com/gatita/ # Room 및 기기 테스트(현재 결제액 방식과 legacy 회귀 검증)
gradle/wrapper/                      # Gradle Wrapper
```

`com.gatita`는 임시 패키지명이며 앱 식별자와 최종 패키지명은 추후 확정합니다.

상세 기획·설계·테스트·보안 문서는 [옵시디언 보관함](.codex/docs/index.md)에서 관리합니다. Obsidian의 **폴더를 보관함으로 열기**에서 이 프로젝트의 `.codex/docs/`를 선택하세요. Codex는 [AGENTS.md](AGENTS.md)에 연결된 같은 문서를 읽습니다.

## 퍼블리싱 UI와 기능 연결

실제 런처(`com.gatita`)에 TAGO 태블릿 레이아웃을 적용하고 기존 Room 기반 기능을 연결했습니다. 여행 시작·복구, 비용 추가/수정/삭제/포함 선택, 정산 확정·요약·QR, 월별 내역은 실제 데이터로 동작합니다. `PublishingApp`은 별도의 샘플 Preview로만 유지하며 실제 런처에 샘플 검수 툴바를 표시하지 않습니다.

차량 SDK는 미연결이므로 자동 주행/시동 인식 대신 수동 기록임을 표시합니다. 설정(이름·운전자 기본값·화면 모드)은 영구 저장됩니다. 수동 일시정지/재개와 재실행 복구, 내역 목록 삭제를 지원합니다. 목록 삭제는 확정 원본을 보존합니다.

[연결 범위와 검수 기록](.codex/docs/publishing-integration-2026-11-09.md)을 참고하세요. 기존 퍼블리싱 문서의 APK 식별자와 빌드 설정은 독립 시안 앱 기준입니다.
