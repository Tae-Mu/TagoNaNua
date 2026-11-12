# 설치 및 실행 가이드 (Windows, Pleos 에뮬레이터)

같이타(TagoNaNua)를 처음 받은 개발자가 빌드하고 Pleos Connect 에뮬레이터에서 실행하기까지의 순서입니다.
Windows 11에서 직접 따라 해 보고 정리했습니다. 직접 확인하지 못한 부분은 "미확인"으로 표시했습니다.

## 1. 준비물

| 항목 | 버전 | 비고 |
|---|---|---|
| JDK | 17 (Temurin 확인) | `JAVA_HOME`을 설정하세요 |
| Android SDK | platform 35, build-tools 35.0.0 | `compileSdk`/`targetSdk` 35, `minSdk` 29 |
| SDK 도구 | platform-tools, emulator, cmdline-tools | Android Studio로 설치하면 함께 들어옵니다 |
| Android Studio | 선택 사항 | AVD 생성과 디버깅에 편합니다 |
| 디스크 여유 | 약 30GB 이상 | 에뮬레이터 이미지 압축 파일 약 4GB, 풀면 `system.img`만 약 18GB |
| 메모리 | 8GB 이상 권장 | AVD 설정 RAM이 4~8GB입니다 |

Gradle은 저장소의 Wrapper(`gradlew`, Gradle 8.9)를 사용하므로 따로 설치하지 않습니다. AGP 8.7.3, Kotlin 2.0.21입니다.

## 2. 소스 받기

```bash
git clone https://github.com/Tae-Mu/TagoNaNua.git
cd TagoNaNua
git checkout loren_dev
```

프로젝트 루트에 `local.properties`를 만들고 SDK 위치를 적습니다(Git에 올라가지 않습니다).

```properties
sdk.dir=C\:\\Users\\<사용자>\\AppData\\Local\\Android\\Sdk
```

Android Studio로 열면 자동으로 만들어집니다. 라이선스 동의가 필요하면 `sdkmanager --licenses`를 실행하세요.

## 3. 빌드

```bash
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # JUnit 단위 테스트
```

- Pleos Vehicle SDK(`ai.pleos.playground:Vehicle:2.3.2`)는 `settings.gradle.kts`에 등록된 Pleos Maven 저장소에서 받습니다.
  제가 빌드했을 때는 별도 인증 없이 내려받았습니다. 받아지지 않으면 Pleos Playground 계정과 SDK 사용 신청 여부를 확인하세요(미확인).
- 버전은 `gradle/libs.versions.toml`에서 관리합니다.

## 4. Pleos Connect 에뮬레이터

일반 Android 에뮬레이터에는 Vehicle Service가 없어서 차량 값을 읽을 수 없습니다. Pleos 에뮬레이터를 써야 합니다.

1. [Pleos Playground](https://developers.pleos.ai)에 가입하고 프로젝트를 만듭니다.
2. 프로젝트 페이지에서 Pleos Connect 에뮬레이터(`crp_car_emulator.zip`, 약 4GB)를 내려받습니다. 42dot 라이선스에 동의해야 합니다.
   다운로드 링크는 계정마다 다를 수 있어 이 문서에 적지 않았습니다.
3. 압축 파일에는 `system.img`, `vendor.img`, `userdata.img`, `kernel-ranchu`, `ramdisk.img`, `source.properties`, `devices.xml` 등이 들어 있습니다.
   파일 이름과 구성이 내려받은 것과 다르면 Pleos 안내를 따르세요.
4. 압축을 풀어 다음 위치에 둡니다. **`source.properties`가 이 폴더에 있어야** SDK Manager가 이미지로 인식합니다.

   ```
   <Android SDK>\system-images\android-34\android-automotive\x86_64\
   ```

   - 이미지는 Android 14(API 34) Automotive, x86_64, 벤더 `42dot`입니다.
   - 긴 경로 때문에 압축 해제가 실패하면(Windows MAX_PATH) 임시 폴더를 거치지 말고 위 폴더에 바로 풀어 주세요.
5. AVD를 만듭니다. Android Studio의 Device Manager에서 새 장치를 만들고, 이미지에 포함된 `devices.xml`의 `Pleos_Connect_v2_0`(2560×1440, 밀도 160) 정의와 위 시스템 이미지를 선택합니다.
   제가 만든 AVD 이름은 `Pleos_Connect_v2.1`입니다.
6. 실행합니다.

   ```bash
   emulator -avd Pleos_Connect_v2.1 -no-snapshot-save
   ```

화면은 2560×1440 가로형 차량 화면입니다. 왼쪽에 차량 UI, 오른쪽에 앱이 뜹니다.

## 5. 앱 설치와 실행

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Automotive 에뮬레이터는 **Android 사용자 10**으로 앱을 실행합니다. 이어지는 `adb` 명령에는 대부분 `--user 10`이 필요합니다.

```bash
adb shell am start --user 10 -n com.gatita/.MainActivity
adb shell pm clear --user 10 com.gatita                 # 앱 데이터 초기화
adb shell "run-as com.gatita --user 10 ls -l databases" # 앱 DB 확인
```

앱이 하단 앱 목록에 보이지 않으면 위 `am start` 명령으로 실행하세요.

## 6. 현재 차량 연동 상태

- 새 여행을 시작하면 Pleos SDK로 **계기판 주행거리**(`Odometer.getCurrentDistanceDriven`)를 읽어 `VehicleSource.PLEOS`로 저장합니다.
  10초 안에 읽지 못하면 시작하지 않고 안내를 보여 줍니다. 0km나 샘플 값으로 대체하지 않습니다.
- 에뮬레이터에서는 주행거리가 `0`으로 읽힙니다. 에뮬레이터 값이며 실제 차량 검증이 아닙니다.
- **연료 %·연비·전비는 읽도록 구현했지만 에뮬레이터에서는 값이 없습니다(`efficiency = null`).**
  - `Fuel`과 `EvBattery`에 연비·전비 API가 각각 4개씩(현재·순간·누적·주유/충전 이후) 있습니다. 반환값은 `Float?`입니다.
  - 단위는 SDK 2.3.2 소스(sources.jar)의 KDoc에 명시되어 있습니다. 내연 `Fuel`은 **L/100km**, 전기 `EvBattery`는 **km/kWh**입니다. 웹 문서 본문에는 단위가 없습니다.
  - Pleos 에뮬레이터에서 8개를 모두 호출했을 때 "지원하지 않는 속성"으로 실패했습니다. 실제 차량이나 속성이 설정된 환경이 필요합니다.
  - 집계 기준은 KDoc에 "마지막 주유/충전 이후", "운행 기간 누적"으로만 적혀 있고, 시동 때 초기화되는지는 확인되지 않았습니다(미확인).
  - 앱은 `getFuelPercent`·`getSinceChargeFuelEconomy`(전기는 `getEvBatteryStateOfCharge`·`getSinceChargeEvEfficiency`)·`getTripSinceRefillDrivingDistance`(m→km)와 숨김·deprecated인 기어 API를 호출하고, 실패하면 해당 값만 `null`로 둡니다.
- 새 여행의 정산은 구간별 유류비 방식(`fuel-segments-v2`)입니다. 에뮬레이터 값은 실제 차량 검증이 아닙니다.

## 7. 테스트

```bash
./gradlew :app:testDebugUnitTest            # 단위 테스트(에뮬레이터 불필요)
./gradlew :app:connectedDebugAndroidTest    # 기기 테스트(에뮬레이터 필요)
```

`PleosVehicleDataSourceTest`는 Pleos 에뮬레이터에서만 의미가 있습니다. 일반 에뮬레이터에서는 실패합니다.

## 8. 주의할 점

- **기기 테스트는 앱을 삭제합니다.** `connectedDebugAndroidTest`가 끝나면 에뮬레이터의 앱 데이터(여행 기록)가 사라집니다. 남겨야 하면 먼저 DB를 백업하세요.
- **DB를 백업할 때 `adb shell ... cat`을 쓰지 마세요.** 줄바꿈이 변환되어 파일이 손상됩니다. 바이너리는 `adb exec-out`이나 `adb push/pull`을 쓰세요.

  ```bash
  adb shell am force-stop --user 10 com.gatita
  adb exec-out "run-as com.gatita --user 10 cat databases/gatita.db" > gatita.db
  ```

  복원할 때는 앱을 한 번 실행해 DB 폴더가 만들어진 뒤 `-wal`, `-shm` 파일을 지우고 `gatita.db`만 덮어씁니다.
- **Git Bash에서 `adb push`의 대상 경로가 바뀝니다.** `/data/...`가 `C:/Program Files/Git/data/...`로 변환됩니다. 앞에 `MSYS_NO_PATHCONV=1`을 붙이세요.
- 에뮬레이터 시계는 PST(UTC-8)입니다. 여행 날짜와 시간이 한국 시간과 다르게 보일 수 있습니다.
- 처음 실행하면 에뮬레이터 부팅과 이미지 로딩이 오래 걸립니다.

## 9. 더 읽을 문서

- 설계와 정책: `.codex/docs/` (`index.md`부터)
- Pleos 가이드: <https://document.pleos.ai/docs/pleos-only/getting-started>
