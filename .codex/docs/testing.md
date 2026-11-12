# 테스트 전략

Kotlin 앱은 JUnit 기반 단위 테스트, Room 기기 테스트, Compose UI 검증으로 나눕니다. Jest는 도입하지 않습니다.

## 최신 UI 보정 검증 (2026-11-10)

후속 거리 입력 제거 작업에서 JUnit은 120건으로 갱신됐다. SDK 미연결/샘플값에서 여행 생성 방지를 추가 확인했다. 해당 정책과 검증은 [UI 보정 후속 기록](ui-parity-2026-11-10.md#후속-정책-수정-거리-입력-제거)을 따른다.

- assembleDebug·lintDebug·assembleDebugAndroidTest 성공, JUnit **119건** 및 직접 ADB 계측 **26건** 성공.
- TAGO_Tablet에서 홈→준비→로고→기록, 수동 30km 구간 입력, 일시정지/재개, 주유 등록 후 기록 복귀, 완료 확인/취소, 완료→정산 확인, 설정/내역을 직접 검수했다.
- 첫 기기 테스트는 에뮬레이터 연결 종료로 중단되어 재시작 후 다시 실행했다. 사용자 DB 삭제 없이 APK를 `install -r`로 갱신했다.
- 이 결과는 UI 수정 시 실행한 기록이며 후속 문서 편집 시 재실행한 결과가 아니다. 세부 범위·증거는 [UI 재정렬 검수](ui-parity-2026-11-10.md)를 따른다.
- 기존 Gradle connectedDebugAndroidTest 환경은 테스트 후 타깃 APK를 제거할 수 있어 사용자 기록 보존이 필요한 QA 기기에서는 사용하지 않는다. 테스트 APK를 빌드·설치 후 직접 `am instrument`로 실행한다.

## 현재 테스트 상태 (2026-11-09)

사용자가 전체 연결·QA·엣지케이스 검증을 명시적으로 요청하여 JUnit 및 기기 테스트를 실행했다. 현재 결제액 방식에 맞게 오래된 ViewModel·화면 테스트를 갱신했고 legacy 계산·저장 회귀 테스트는 유지했다. 상세 결과와 미검증 범위는 [전체 연결 검수](full-integration-qa-2026-11-09.md)를 따른다.

검증 범위: 입력 경계값·단가·날짜·Unicode 길이, 1~8명 분담/운전자 제외, 포함·제외 합계, 중복/오래된 요청, 확정 불변, 트랜잭션 롤백, v1/v2→v4 마이그레이션, 설정 초기화/복구/저장 오류, 일시정지/재개, 목록 삭제, Compose 빈 상태/오류/중복 제출/QR 접근성.

## 실행 및 보고

테스트는 사용자가 요청할 때 실행합니다. 실행하지 못한 검증은 이유를 명시하고 샘플·에뮬레이터·실차 결과를 구분합니다. 이전 버전 결과를 새 구현의 결과로 보고하지 않습니다. 빌드 성공을 테스트 통과로 표현하지 않습니다.


## 퍼블리싱 기능 연결 이후 추가 검증 (2026-11-09)

기존 테스트 스위트는 실행하지 않았다. UI 연결 작업 중 `assembleDebug`·`lintDebug` 및 TAGO_Tablet(emulator-5556)의 실제 탭/입력/스크롤/프로세스 재실행으로 검수했다. 초기 기능 구현 당시의 "Lint·수동 QA 미실행" 문구는 당시 기록이며 최신 범위는 [퍼블리싱 연결 검수](publishing-integration-2026-11-09.md)를 따른다. 마이그레이션·실차·QR 휴대폰 리더 검증은 여전히 별도다.

이전 절의 “기존 테스트 스위트 미실행”은 최초 연결 작업 당시 기록이다. 후속 전체 연결 검수에서 실행 완료했다.


## 2026-11-12 구간별 유류비 계산기

`FuelSettlementCalculatorTest`(16건)·`FuelTrackingCalculatorTest`(15건: 구간 구성·정차 판정·플래너)·`FuelTrackingUseCasesTest`(8건: 측정 기록·주유 감지·종료 조건·보정)와 기존 순수 계산·유스케이스 테스트(`SettlementCalculatorTest`, `ShareAllocatorTest`, `PaymentEdgeCasesTest`, `TripAndSettlementUseCasesTest`, `PleosValueMapperTest`, 합계 117건)를 Android SDK 없이 별도 JVM 프로젝트(JDK 21, Kotlin 2.0.21, JUnit 4.13.2)로 컴파일해 전부 통과시켰다. 이 프로젝트는 저장소 밖 임시 폴더에서 `domain` 전체와 `PleosValueMapper.kt`만 포함했으며 저장소에는 두지 않았다. `:app` 모듈 전체(Room·Compose·ViewModel) 빌드·JUnit·Lint·기기 테스트는 이번에도 실행하지 못했다. 같은 날 삭제한 수동 구간 저장 테스트와 `EfficiencyUnit.KM_PER_KWH` 추가가 `:app`에 미치는 영향은 컴파일로 확인하지 못했다.