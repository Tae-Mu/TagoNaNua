# Pleos Vehicle SDK 조사 결과 — 연비·시동 신호 (2026-11-10)

연비 기록과 "시동 ON/OFF 기준 구간 자동 저장"을 구현하기 전에 SDK와 에뮬레이터에서 직접 확인한 내용입니다.
**확인한 사실**, **추정**, **미확인**을 구분해 적었습니다. 이 문서의 결론은 실차 검증 전까지 확정이 아닙니다.

## 조사 방법과 환경

- SDK: `ai.pleos.playground:Vehicle:2.3.2`. AAR의 `classes.jar`를 `javap`로 열어 인터페이스·리스너·상수를 확인했습니다. 공식 문서와 별개로 실제 배포 파일 기준입니다.
- 에뮬레이터: Pleos Connect v2.1 AVD(Android 14 Automotive, x86_64, `ro.build.type=user`). 앱 안에서 SDK를 호출하는 기기 테스트와 `dumpsys car_service --hal`로 확인했습니다.
- 에뮬레이터 결과는 실차 검증이 아닙니다. 에뮬레이터 값(주행거리 `0.0` 등)을 실제 차량 값으로 보고하지 않습니다.

## 1. 연비·전비 API

| 인터페이스 | 값 | 반환 | 구독 |
|---|---|---|---|
| `Fuel` | `getCurrentFuelEconomy`, `getInstantaneousFuelEconomy`, `getCumulativeFuelEconomy`, `getSinceChargeFuelEconomy` | `Float?` | `FuelEconomyListener` |
| `EvBattery` | `getCurrentEvEfficiency`, `getInstantaneousEvEfficiency`, `getCumulativeEvEfficiency`, `getSinceChargeEvEfficiency` | `Float?` | `EvBatteryEfficiencyListener` |

- 각 값마다 `check…Capabilities`(지원 여부)가 있고, 권한은 `CAR_ENERGY`입니다(`Fuel` 문서 기준. `EvBattery`의 권한은 미확인).
- 전기차용 전비 API가 `Fuel`과 별도로 있습니다. 차량 종류에 따라 읽을 쪽이 다릅니다.
- 트립 단위 구독(`TripFuelEconomyListener`, `TripEvEfficiencyListener`)도 있습니다.

### 단위와 집계 기준 — 미확인

- 반환값이 `Float` 하나이고 **단위(km/L, L/100km, km/kWh)가 문서와 SDK 어디에도 없습니다.** `constant.unit` 패키지에는 거리·속도·온도·EV 배터리용량(Wh/Ah/kWh) 단위만 있고 연비·전비 단위는 없습니다.
- 차량 표시 단위를 읽는 API는 온도(`getCurrentTemperatureDisplayUnit`)뿐입니다.
- `Cumulative`(누적)와 `SinceCharge`(충전·주유 이후)가 언제부터 집계되는지 문서에 없습니다. 누적 평균을 구간 평균으로 취급하지 않는 기존 원칙([architecture](architecture.md))은 그대로 유지합니다.
- 차량이 내연기관인지 전기차인지 알려 주는 읽기 API는 찾지 못했습니다. `CarInfo`는 VIN, CRN, `getCapabilityInfo`만 있고, `getCapabilityInfo`는 추가 권한이 필요합니다(`SecurityException`). `FuelType` 상수는 제어용 패키지에 있습니다.

### 에뮬레이터 결과

8개 API(`Fuel` 4개, `EvBattery` 4개)와 각 `check…Capabilities`를 모두 호출했고 **전부 실패**했습니다(`NOT_SUPPORTED_PROPERTY` 또는 "carPropertyConfig 목록에 없음").

- 확인한 속성 ID 7개(`Fuel` 순간·충전 이후·누적 = `0x21600be0/be1/be2`, `EvBattery` 순간·충전 이후·누적 = `0x21600b13/b14/b15`, 주유 잔량 `0x21400be5`)는 Vehicle HAL 속성 목록 312개에 모두 없습니다.
- "현재(Current)" 2종과 트립 거리 4종도 미지원 응답이었으나 속성 ID는 확인하지 못했습니다.
- 에뮬레이터는 `user` 빌드라 `adb root`와 `cmd car_service`의 속성 설정 명령이 막혀 있고, 속성 목록은 읽기 전용 시스템 이미지에 있어 **설정으로 속성을 추가할 수 없습니다**(추정: 설정창에서도 등록된 속성만 바꿀 수 있음. 설정창은 열어 보지 않았습니다).

함께 읽은 값(참고용, 단위는 추정):

| API | 에뮬레이터 값 | 비고 |
|---|---|---|
| `Odometer.getCurrentDistanceDriven` | `0.0` | 앱이 km로 사용 중. 고정값으로 보임 |
| `Odometer.getRangeRemainingDistance` | `500000.0` | 단위가 m일 가능성(추정) |
| `EvBattery.getEvBatteryStateOfCharge` | `100` | % |
| `EvBattery.getEvBatteryLevel` | `77400.0` | 단위 미확인(Wh로 보임, 추정) |
| `EvBattery.getEvBatteryCapacity`, `CarInfo.getCapabilityInfo` | `SecurityException` | 추가 권한 필요 |
| `Fuel.getFuelPercent`, 트립 거리 4종 | 미지원 | |

→ 에뮬레이터에서는 연비 단위를 알아낼 수 없습니다. 실차 또는 연비 속성이 구성된 환경, 그리고 Pleos 문서 확인이 필요합니다.

## 2. 시동(Ignition) 신호

- SDK에 `IgnitionState`(`UNDEFINED`, `LOCK`, `OFF`, `ACC`, `ON`, `START`) 상수가 있지만, **이 값을 반환하거나 구독하는 API·리스너가 SDK 2.3.2에 없습니다.** 클래스 전체에서 참조하는 곳이 없습니다.
- 안드로이드 오토모티브 표준 속성 `IGNITION_STATE`(`0x11400409`)도 에뮬레이터의 차량 속성 목록에 없습니다.
- 시동과 가까운 신호는 `Gear.registerGearSelection`(`GEAR_PARK`, `DRIVE`, `REVERSE`, `NEUTRAL`, `UNKNOWN`)과 주행거리·트립 시간 구독입니다. 에뮬레이터에는 기어(`0x11400400`)와 차량 속도(`0x11400401`) 속성이 있습니다. **에뮬레이터에서 기어 이벤트가 실제로 전달되는지는 미확인**입니다.
- `HvacPowerStateListener`(공조 전원), `OilLevelStateListener` 등은 시동과 무관합니다.

기존 문서가 예로 든 "시동 OFF 알림"에 해당하는 이벤트가 SDK에 없으므로, 시동 ON/OFF를 구간 트리거로 쓰려면 아래 중 하나를 정해야 합니다.

| 방법 | 설명 | 한계 |
|---|---|---|
| A. 기어 전환으로 대체 | P→D/R = 구간 시작, D→P 일정 시간 유지 = 구간 끝 | 시동이 아니라 주행 시작/종료. 시동을 끄지 않은 정차도 구간을 끝냄. 유지 시간(예: 30초)은 임의 값 |
| B. 앱 생명주기로 대체 | 앱 실행/종료를 시동으로 간주 | 차량 시동과 무관. 앱이 꺼지면 이벤트를 놓침 |
| C. Pleos에 시동 이벤트 문의 | 가장 정확 | 답변 전까지 구현 보류. 다음 SDK 버전 노출 여부는 추측 |

권장안(미결정): A로 구현하되 구간 경계 이벤트를 추상화해 두고, C를 병행합니다. 사용자 확인 전이므로 확정이 아닙니다.

## 3. 구간 자동 저장이 아직 없는 이유

- **저장 계층은 준비돼 있습니다.** `SaveSegmentUseCase`는 `eventId` 중복 방지, 이전 구간 끝을 시작으로 이어받기, 연비 `null` 허용을 지원합니다.
- **없는 것**: 구간을 자르는 이벤트 감지(위 2절), 앱이 꺼져 있을 때의 처리.
- 리스너는 앱 프로세스가 살아 있을 때만 동작합니다. 시동 OFF 후 저장 가능 여부, 재시동 시 구독 복구는 실차 검증 대상입니다([planning](planning.md) 검증 과제).
- 구간·기록 시간의 정의(시동 기준/주차 기준/경과 시간)는 기획에 정해져 있지 않습니다.
- 에뮬레이터는 주행거리가 `0.0` 고정이고 트립 거리·연비가 미지원이라 구간 거리와 연비를 검증할 수 없습니다.

현재 "여행 마치기"는 차량 값을 읽지 않고 "거리 기록 없음 = 0km"로 마칩니다. 따라서 지금은 어떤 구간도 저장되지 않고, 연비도 기록되지 않습니다.

## 4. 현재 구현 상태 (2026-11-10, `loren_dev`)

- `PleosVehicleDataSource`가 `Odometer.getCurrentDistanceDriven`만 읽고 `efficiency = null`을 돌려줍니다. 0이나 추측한 단위로 채우지 않습니다.
- `AppContainer`가 `PleosVehicleDataSource`를 사용합니다. 새 여행 시작은 PLEOS 스냅샷이 있어야 하고(10초 시간 초과), 에뮬레이터에서 시작까지 확인했습니다. 이는 실차 검증이 아닙니다.
- 연비 수동 입력은 없습니다.

## 5. 결정이 필요한 것 (미결정)

1. **연비 단위**: 사용자가 km/L로 가자고 했으나 SDK로 확인된 사실이 아닙니다. 구현하면 "단위 미확인 가정"으로 표시해 저장해야 합니다. 에뮬레이터 차량이 전기차(전비 단위일 가능성)라 km/L가 맞지 않을 수 있습니다.
2. **연비 수집 범위**: "받을 수 있으면 자동 수신, 받을 수 없으면 직접 입력"을 요청받았으나, 거리·연비 수동 입력 제거(2026-11-10 사용자 확인, [publishing](publishing.md))와 충돌합니다. 확정하려면 `publishing.md`, `planning.md`, `architecture.md`의 해당 문장과 [결정 기록](decisions.md)을 함께 고쳐야 합니다. [architecture](architecture.md)의 "자동 기록이 불가능한 경우 정차 후 수동 구간 저장 제공"은 검토 과제로만 적혀 있습니다.
3. **구간 트리거**: 위 2절의 A/B/C.
4. **Pleos에 확인할 질문**: ① 연비·전비 반환값의 단위와 집계 기준, ② 시동 상태를 읽거나 구독하는 방법, ③ 연비 속성이 구성된 에뮬레이터 이미지 또는 속성 설정 방법.

## 6. 문서와 구현의 차이

다음 문장은 현재 코드와 맞지 않습니다. 원본 문서이므로 이 조사에서는 고치지 않고 차이만 기록합니다.

- `publishing.md` 9행, `ui-parity-2026-11-10.md`, `architecture.md` 79행: "실제 SDK 어댑터가 없고 `UnavailableVehicleDataSource`가 실패를 반환해 새 여행 시작이 보류"라는 내용 → 현재는 `PleosVehicleDataSource`가 연결돼 시작 가능합니다(주행거리만).
- `README.md`: "Pleos Vehicle SDK (미연동)", "SDK 어댑터가 없어 시작을 보류" 등 → 위와 같은 사유로 현재와 다릅니다.
