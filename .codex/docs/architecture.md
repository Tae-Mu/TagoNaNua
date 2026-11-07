# 프로젝트 설계

## 처리 흐름

화면 → ViewModel → UseCase → Repository → Room으로 기록을 저장합니다. 차량 신호는 VehicleDataSource를 통해 조회합니다.

- feature: 화면, ViewModel, 화면 상태를 기능별로 배치합니다.
- domain: 정산 계산과 여행 처리 규칙을 담당하며 Android, Room, Pleos SDK에 의존하지 않습니다.
- data: Room과 Pleos SDK를 사용하여 domain의 인터페이스를 구현합니다.
- di: 실제 차량 데이터 또는 샘플 데이터 구현을 선택하고 필요한 객체를 연결합니다.

처음에는 app 모듈 하나를 사용합니다. 구현이 없는 클래스 파일은 미리 만들지 않습니다.

## 예정 진입 파일

- GatitaApplication.kt: 앱 공통 객체 초기화
- MainActivity.kt: Compose 화면 진입
- di/AppContainer.kt: DB 및 저장소 객체 연결
- navigation/AppNavHost.kt, Routes.kt: 화면 이동
- data/local/AppDatabase.kt: Room DB 구성
- domain/calculator/SettlementCalculator.kt: 유류비와 분담액 계산
- domain/vehicle/VehicleDataSource.kt: 차량 데이터 조회 인터페이스
- data/vehicle/PleosVehicleDataSource.kt: 실제 차량 연동
- data/vehicle/SampleVehicleDataSource.kt: 개발 및 시연용 데이터

위 파일과 AndroidManifest.xml, Gradle 빌드 파일은 실제 개발 시작 시 추가합니다.

## DB 설계

테이블과 저장 정책은 [DB 설계](database.md)에서 관리합니다.

## 계산 규칙

- 구간 연비인 경우 사용량은 거리 ÷ 연비로 계산합니다.
- 누적 평균 연비인 경우 동일 집계 기준의 누적 사용량 차이를 계산합니다.
- 집계 기준을 확인할 수 없으면 차량 기준 연비를 이용한 전체 여행 추정치로 표시합니다.
- 중간 계산의 정밀도를 유지하고 합산 후 최종 총액만 원 단위 HALF_UP으로 반올림합니다.
- 분담액의 나머지는 참여자 순서대로 1원씩 배분합니다.
- 예시: 총액 3,685원, 4명 → 922원, 921원, 921원, 921원.

## 저장 및 공유

여행 기록과 정산은 차량의 Room에 보관합니다. 인터넷 없이도 기록 및 계산이 가능하도록 구성합니다. 앱 재실행 시 진행 중 여행을 복구합니다.

첫 버전의 QR에는 정산 요약 텍스트를 담습니다. 실제 표시 방식과 읽기 가능 여부는 휴대폰 QR 리더로 검증합니다. 웹페이지 공유가 필요하면 정적 페이지를 별도로 배포하고 QR 링크에 정산 데이터를 포함하는 방식을 검토합니다.

자동 동기화와 서버 백업은 초기 범위에서 제외합니다. 앱 데이터 삭제 시 로컬 기록도 삭제됩니다.

## 차량 연동 확인 과제

- 연비 단위와 집계 시작점, 시동 및 주유 시 초기화 여부
- 시동 OFF 직후 데이터 조회와 DB 저장 가능 여부
- 재시동 시 구독 복구 가능 여부
- 중복 이벤트 및 조회 실패 처리
- 자동 기록이 불가능한 경우 정차 후 수동 구간 저장 제공
- 실제 차량 데이터와 샘플 데이터 출처 구분
