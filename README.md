# 같이타 정산 (TagoNaNua)

Pleos 차량의 주행거리와 연비를 바탕으로 예상 유류비를 계산하고 동승자별 분담액을 공유하는 차량 앱입니다.

## 현재 상태

최신 HTML 시안을 기반으로 Jetpack Compose 퍼블리싱 앱을 추가했습니다. 홈, 출발 준비/인트로, 여행 기록/복원/완료, 비용 종류/입력/내역, 정산 목록/상세, 여행 내역, 설정의 14개 화면을 샘플 데이터로 확인할 수 있습니다.

**UI 검토용 앱입니다.** Room, 차량 SDK, 저장, 실제 정산 계산은 연결하지 않았습니다. 입력·선택·화면 전환만 제공하며 금액은 고정 샘플입니다. 출발 인트로는 입력한 여행 이름과 앱 로고를 표시하고 3.32초 후 전환을 시작합니다. 바로 기록 보기로 건너뛸 수 있습니다.

### 빌드 및 미리보기

JDK 17 이상과 Android SDK 36이 필요합니다. Android Studio에서 이 폴더를 열고 Gradle Sync 후 app을 실행하세요. 앱 식별자는 기존 앱과 구분하는 `com.gatita.publishing`입니다.

```sh
./gradlew :app:assembleDebug :app:lintDebug
```

SDK 경로는 ANDROID_HOME 또는 개인 local.properties로 지정합니다. local.properties는 커밋하지 않습니다. APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 하단 **퍼블리싱 · 화면 선택**에서 각 화면을 열고 기본/빈 상태/오류/로딩/주행 표시를 확인하세요. 600dp 이상은 사이드 메뉴, 좁은 폭은 하단 메뉴입니다.

구현 범위와 검수 현황은 [.codex/docs/publishing.md](.codex/docs/publishing.md)를 참고하세요.

## 개발 구성

| 구분 | 기술 |
|---|---|
| 언어 | Kotlin |
| UI | Jetpack Compose |
| 로컬 데이터 저장 | Room / SQLite |
| 차량 연동 | Pleos Vehicle SDK |
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
    │   │   │   └── usecase/         # 여행 및 정산 처리
    │   │   ├── data/
    │   │   │   ├── local/entity/    # Room 테이블
    │   │   │   ├── local/dao/       # DB 조회 및 저장
    │   │   │   ├── local/migration/ # DB 구조 변경
    │   │   │   ├── mapper/          # DB 모델과 도메인 모델 변환
    │   │   │   ├── repository/      # 저장소 구현
    │   │   │   └── vehicle/         # Pleos 및 샘플 데이터 구현
    │   │   └── feature/
    │   │       ├── home/            # 홈
    │   │       ├── trip/            # 진행 중 여행
    │   │       ├── settlement/      # 정산 입력 및 결과
    │   │       ├── history/         # 과거 내역
    │   │       └── share/           # QR 공유
    │   └── res/
    │       ├── drawable/           # 이미지와 아이콘
    │       └── values/             # 문자열 등 리소스
    ├── test/java/com/gatita/        # 정산 및 여행 규칙 단위 테스트
    └── androidTest/java/com/gatita/ # Room 및 기기 테스트
gradle/wrapper/                      # Gradle Wrapper 추가 예정
```

`com.gatita`는 폴더 구성을 위한 임시 패키지명입니다. 앱 식별자와 최종 패키지명은 Android 프로젝트 설정 시 확정합니다. 빈 폴더의 `.gitkeep`은 Git에 폴더를 보존하기 위한 파일입니다.

상세 기획·설계·테스트·보안 문서는 [옵시디언 보관함](.codex/docs/index.md)에서 관리합니다. Obsidian의 **폴더를 보관함으로 열기**에서 이 프로젝트의 `.codex/docs/`를 선택하세요. Codex는 [AGENTS.md](AGENTS.md)에 연결된 같은 문서를 읽습니다.
