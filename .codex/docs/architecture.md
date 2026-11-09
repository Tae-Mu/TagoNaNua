# 프로젝트 설계

## 처리 흐름

화면 → ViewModel → UseCase → Repository → Room으로 기록을 저장합니다. 차량 신호는 VehicleDataSource를 통해 조회합니다. 서버·HTTP API는 없으며 "API"는 ViewModel이 호출하는 앱 내부 UseCase 계약입니다([html-api-spec](html-api-spec.md)).

빈 여행 닫기(`DiscardEmptyTripUseCase`, `AppContainer.discardEmptyTrip`): 하나의 Room 트랜잭션에서 여행·구간·비용을 다시 읽어 IN_PROGRESS이고 구간·비용·종료 거리가 모두 없을 때만 `EmptyTripRepository.deleteIfEmptyActive`로 그 여행 한 건을 삭제합니다. 삭제 쿼리도 같은 조건(`NOT EXISTS` 구간·비용)을 다시 검사하므로 조건이 어긋나면 아무것도 지우지 않고 `TRIP_NOT_EMPTY`로 실패합니다. 완료·확정 여행은 `TRIP_NOT_IN_PROGRESS`/`TRIP_LOCKED`로 거부하며, 일반 여행 삭제 기능은 아닙니다. 성공하면 TripViewModel이 `Discarded` 이벤트를 보내 홈으로 이동하고, 실패하면 오류 문구만 표시하며 기존 기록은 그대로 둡니다.

- feature: 화면, ViewModel, 화면 상태를 기능별로 배치합니다.
- domain: 정산 계산과 여행 처리 규칙을 담당하며 Android, Room, Pleos SDK에 의존하지 않습니다.
- data: Room과 Pleos SDK를 사용하여 domain의 인터페이스를 구현합니다.
- di: 실제 차량 데이터 또는 샘플 데이터 구현을 선택하고 필요한 객체를 연결합니다.

app 모듈 하나를 사용합니다.

## 주요 진입 파일

- GatitaApplication.kt, MainActivity.kt: 앱 초기화와 Compose 진입
- di/AppContainer.kt: DB 및 저장소 객체 연결
- data/local/AppDatabase.kt: Room DB(현재 버전 4)
- data/local/migration/Migrations.kt: v1 → v2 마이그레이션
- domain/calculator/PaymentSettlementCalculator.kt: 비용 합계·분담액·확정 스냅샷(현재 제품의 계산)
- domain/calculator/ShareAllocator.kt: 정수 원 나머지 배분
- domain/calculator/SettlementCalculator.kt: 옛 추정 계산(v1 이력 호환용, 새 정산에 사용하지 않음)
- domain/vehicle/VehicleDataSource.kt, data/vehicle/SampleVehicleDataSource.kt: 차량 데이터 인터페이스와 샘플 구현
- data/vehicle/PleosVehicleDataSource.kt: 실제 차량 연동(미구현)
- navigation/AppNavHost.kt: 화면 이동

## 계산 규칙 (결제액 방식, calculationVersion `payments-v1`)

- 총액 = 정산에 포함된(`included`) 비용의 실제 입력 금액 합계. Long 합계는 checked-add로 오버플로를 거부합니다.
- FUEL의 주유량(L) = 금액 ÷ 리터당 단가, 소수 3자리 HALF_UP. 화면 표시 전용이며 정산에 다시 곱하지 않습니다. 금액·단가에 Double을 쓰지 않습니다.
- 대상 인원: 운전자 포함이면 `people`, 제외면 `people - 1`. 1명이면 포함으로 정규화합니다.
- `base = total / 대상 인원`(정수 나눗셈), `remainder = total % 대상 인원`. 대상자 순서(운전자 ordinal 0 → 동승자)대로 앞 `remainder`명이 1원씩 더 부담합니다. 제외된 운전자는 0원 행으로 보존합니다.
- 중간 반올림이 없습니다(원 단위 정수 합이므로 반올림 자체가 총액에 개입하지 않음). 분담액 합계는 항상 총액과 같습니다.
- 예: 55,400원 / 4명 포함 → 13,850원씩, 제외 → 0·18,467·18,467·18,466원. 3,685원 / 4명 → 922·921·921·921원.
- 거리·소요시간·연비는 `RecordingMetadata`(품질 VALID/PARTIAL/MISSING)로만 산출하며 측정이 없거나 잘못되어도 확정을 막지 않고 0으로 대체하지 않습니다.
- 연비 원본값·단위·집계 기준은 구간(segment)에 계속 저장하지만 정산 계산에는 쓰지 않습니다. 누적 평균을 구간 평균으로 취급하지 않는 원칙은 유지합니다.

## 확정과 일관성

- 확정 조건: 여행 FINISHED, 포함 비용 1건 이상, 확정 요청의 expectedRevision이 현재 여행 revision과 일치.
- 확정은 하나의 트랜잭션에서 비용 재조회 → 계산 → 정산·비용 스냅샷(제외 항목 포함)·분담액 저장 → 여행 SETTLED 전환을 수행합니다. 확인 후 비용이 바뀌면 STALE_REVISION으로 거부하고 새 미리보기를 보여줍니다.
- 비용 등록과 확정은 호출자가 만든 requestId로 이중 탭을 식별합니다.
- SETTLED 여행은 비용·분담 정책을 바꿀 수 없으며 DB 트리거가 확정 정산의 UPDATE/DELETE를 거부합니다.
- 옛 v1 정산은 `SettlementRecord.Legacy`로 구분해 읽기만 합니다. 결제액 정산은 `SettlementRecord.Payments`입니다.

## 저장 및 공유

여행 기록과 정산은 차량의 Room에 보관합니다. 인터넷 없이도 기록 및 계산이 가능하며 앱 재실행 시 진행 중 여행을 복구합니다. 테이블은 [DB 설계](database.md)를 따릅니다.

첫 버전의 QR에는 정산 요약 텍스트를 담습니다. 송금·입금 확인이 아니며 암호화나 접근 제어도 아닙니다. 휴대폰 QR 리더 표시는 실기기 검증 대상입니다. 자동 동기화와 서버 백업은 초기 범위에서 제외하며 앱 데이터 삭제 시 로컬 기록도 삭제됩니다.

## 차량 연동 확인 과제

- 연비 단위와 집계 시작점, 시동 및 주유 시 초기화 여부
- 시동 OFF 직후 데이터 조회와 DB 저장 가능 여부, 재시동 시 구독 복구 가능 여부
- 중복 이벤트 및 조회 실패 처리
- 자동 기록이 불가능한 경우 정차 후 수동 구간 저장 제공
- 실제 차량 데이터와 샘플 데이터 출처 구분
- html-api-spec의 PARKED 확인 후 비용 편집은 SDK 신호가 확정되기 전까지 미구현


## 실제 앱 UI 연결 (2026-11-09)

`AppNavHost`의 Route들은 기존 기능별 ViewModel을 유지하고 TAGO 퍼블리싱 컴포넌트를 재사용한다. `ConnectedShell`은 태블릿 사이드바 및 좁은 폭 하단 메뉴를 담당한다. 정산·내역·비용의 표시 데이터는 기존 UseCase/Room에서 가져온다. 샘플 `PublishingApp`은 런처와 분리된다. 화면 연결 범위와 차량 미연동 표현은 [연결 기록](publishing-integration-2026-11-09.md)에 정리했다.

## 전체 연결 보완

SettingsViewModel → SaveSettingsUseCase → SettingsRepository → Room으로 설정을 저장한다. 앱 루트는 ViewModel의 저장된 테마를 따른다. HomeViewModel은 새 여행의 운전자 기본값을, SettlementViewModel은 표시 이름 기본값을 읽는다. 이미 확정된 정산은 바꾸지 않는다.

TripViewModel → SetTripPausedUseCase → TripSessionRepository로 수동 일시정지/재개를 저장한다. HistoryViewModel → HideTripUseCase는 완료 여행을 목록에서 숨기며 확정 불변 트리거를 우회하지 않는다. 숨김은 UI 확인을 거친다. 실제 영구 삭제 기능과 구별한다.

비용 수정에 로드한 revision을 연결하고 충돌 시 입력을 보존한다. 성공한 비용 폼은 화면 이동 전에 재탭해도 두 번째 저장을 실행하지 않는다.
