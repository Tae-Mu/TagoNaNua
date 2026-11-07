# 같이타 정산 (TagoNaNua)

Pleos 차량의 주행거리와 연비를 바탕으로 예상 유류비를 계산하고 동승자별 분담액을 공유하는 차량 앱입니다.

## 현재 상태

개발 전 폴더 구조와 설계 문서만 준비한 상태입니다. Kotlin 구현 코드, Android Manifest, Gradle 설정 및 Wrapper 실행 파일은 아직 추가하지 않았으므로 현재 빌드하거나 실행할 수 없습니다.

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

설계 세부 사항은 [프로젝트 설계](docs/architecture.md)를 참고합니다.
