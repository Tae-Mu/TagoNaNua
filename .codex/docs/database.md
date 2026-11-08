# DB 설계

Room을 통해 차량 내부 SQLite에 저장합니다. 현재 스키마 버전은 **2**이며 이력은 `app/schemas/com.gatita.data.local.AppDatabase/1.json`, `2.json`입니다. 컬럼 수준 상세는 [html-api-spec](html-api-spec.md) 7장(제안)과 실제 Room 엔티티를 함께 봅니다. 구현은 명세와 일부 다르며 차이는 아래에 적었습니다.

## 현재 테이블 (v2)

| 테이블 | 용도 | 비고 |
|---|---|---|
| trips | 여행, 상태 IN_PROGRESS/FINISHED/SETTLED | v2에서 name, tripDate, zoneId, vehicleLabel, people, includeDriver, revision, createdAt/updatedAt 추가. `activeSlot` 유일 인덱스로 진행 중 여행 1건 제한 |
| segments | 주행 구간, 연비 원본값·단위·집계 범위 | 기록 정보. 정산 계산에 쓰지 않음 |
| costs | FUEL/PARKING/TOLL/OTHER 실제 결제 금액 | amountWon 1~10,000,000, FUEL만 unitPrice·paidOn 필수, included 기본 1, requestId 유일 |
| payment_settlements | 확정된 결제액 정산 | totalWon, fuelTotalWon, otherTotalWon, people, includeDriver, splitPeople, sourceTripRevision, calculationVersion `payments-v1`, 기록 메타데이터 |
| payment_settlement_costs | 확정 당시 비용 복사본 | 제외 비용 포함. originalCostId는 추적용이며 FK 아님 |
| payment_settlement_shares | 분담액 | PK(settlementId, ordinal). 운전자 ordinal 0은 제외해도 0원 행 보존 |
| fuel_prices, settlements, settlement_items, settlement_shares | **v1 옛 추정 정산 이력** | 읽기 전용 legacy. 새 정산에 사용하지 않음 |

주유량(L)은 파생값이므로 저장하지 않습니다. 새 `fuel_prices` 입력은 현재 흐름에 없습니다(v1 테이블만 이력으로 남김).

## 관계 및 일관성

- 여행 1 : 비용 0~N. 비용 외래키는 CASCADE, 정산 관련 외래키는 RESTRICT입니다.
- 확정은 정산·비용 스냅샷·분담액을 하나의 트랜잭션으로 저장하고 여행을 SETTLED로 바꿉니다. 정산 합계와 분담액 합계가 일치해야 합니다.
- 금액은 정수 원(Long/INTEGER), 단가·거리는 정규화한 십진 문자열입니다. 금액·단가에 부동소수점을 쓰지 않습니다.
- DB 트리거(`ImmutabilityTriggers`)가 DB를 열 때마다 보장하는 규칙: 확정 정산 스냅샷(v1·v2)의 UPDATE/DELETE 거부, SETTLED 여행의 비용 변경 거부, 비용 금액·종류별 null 규칙, 분담 정책(1~8명, 1명이면 운전자 포함). 트리거는 Room 스키마 JSON에 나타나지 않습니다.
- 중복 구간 이벤트는 (tripId, eventId), 중복 비용 등록·중복 확정은 requestId 유일 인덱스로 식별합니다.
- 실패한 조회를 0으로 저장하지 않습니다. 거리 측정이 없으면 null/MISSING이며 확정은 가능합니다.
- 앱 재실행 시 진행 중 여행을 복구합니다. 화면 메모리를 영속 데이터로 간주하지 않습니다.

## v1 → v2 마이그레이션

`Migrations.MIGRATION_1_2`는 데이터를 삭제하거나 다시 쓰지 않습니다. 파괴적 fallback은 사용하지 않습니다.

- v1 테이블은 그대로 두고 trips에 컬럼만 추가, 새 테이블 4개(costs, payment_settlements, payment_settlement_costs, payment_settlement_shares)를 생성합니다.
- v1 여행 보정값: name '새로운 여행', vehicleLabel '', revision 0, tripDate = startedAt을 **UTC**로 해석한 날짜, zoneId 'UTC'(v1은 시간대를 저장하지 않음), people = 옛 정산이 있으면 그 headcount(8 초과 가능), 없으면 1, includeDriver 1.
- v1 정산은 **옛 추정 정산(legacy)**으로 계속 읽히며 결제액 정산으로 재계산하거나 변환하지 않습니다.
- 마이그레이션의 실제 데이터 보존은 기기 테스트(`MigrationTest`)로 검증해야 하며, 새 구현에서는 아직 실행하지 않았습니다([테스트 전략](testing.md)).

## html-api-spec과의 차이

| 명세 제안 | 현재 구현 |
|---|---|
| UUID 문자열 PK | Long autoincrement PK |
| 상태 RECORDING/PAUSED/COMPLETED/SETTLED | IN_PROGRESS/FINISHED/SETTLED (PAUSED 없음) |
| active_trip 싱글턴 테이블 | trips.activeSlot 유일 인덱스 |
| command_receipts 멱등 테이블 | 없음. costs/payment_settlements의 requestId 유일 인덱스만 사용 |
| app_settings | 없음 |
| trip_segments 컬럼 구성 | 기존 v1 segments 재사용 |
| 모든 FK CASCADE(여행 삭제) | 일반 여행 삭제 기능 없음. 예외로 구간·비용이 없는 IN_PROGRESS 여행만 `TripDao.deleteIfEmptyActive`(조건부 DELETE, 트랜잭션 내 재검사)로 삭제. 구간·정산 FK는 RESTRICT, 비용 FK는 CASCADE라 조건에서 비용 부재를 반드시 확인 |

Room 스키마 이력을 app/schemas에 관리하고 기존 데이터를 유지하는 마이그레이션을 작성합니다. 계산 정책은 [아키텍처](architecture.md)를 따릅니다.
