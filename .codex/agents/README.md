# Codex 역할 문서

이 폴더의 Markdown은 역할별 작업 지침이다. 별도의 런타임 에이전트 등록 파일은 아니며, 파일 생성만으로 자동 실행되거나 병렬 작업이 시작되지 않는다. 루트 AGENTS.md가 프로젝트 공통 기준이다.

| 역할 | 문서 | 담당 |
|---|---|---|
| 기획 | [planner](planner.md) | 요구사항과 작업 순서 |
| 디자인 | [designer](designer.md) | UX와 시각 규칙 |
| 프론트엔드 | [frontend](frontend.md) | Compose 화면 |
| 데이터·도메인 | [backend](backend.md) | Room과 정산 로직 |
| 차량 연동 | [vehicle](vehicle.md) | Pleos SDK와 이벤트 |
| 빌드·자동화 | [cicd](cicd.md) | Gradle, CI, 배포 준비 |

## 사용 예시

- `.codex/agents/planner.md를 읽고 여행 시작 기능의 작업 계획을 작성해줘.`
- `.codex/agents/designer.md와 frontend.md를 읽고 정산 화면을 구현해줘.`
- `.codex/agents/backend.md를 읽고 Room 여행 저장 기능을 구현해줘.`

backend라는 이름은 익숙한 역할 구분을 유지하기 위한 것이며 서버 구축을 의미하지 않는다. 차량 연동은 권한과 생명주기 검증이 별도로 필요하므로 vehicle로 나눈다. CI/CD는 초기에는 CI와 APK 생성에 집중한다.

역할 간 전달 시 목표, 변경 파일, 입력·출력 규약, 완료 조건, 검증 결과와 미확인 사항을 남긴다. 공통 모델·DI·Gradle 변경은 관련 역할과 조율하고 한 작업에서 통합한다.

## 문서 원본

상세 기획·설계·테스트·보안 문서는 옵시디언에서 관리한다. 보관함과 실제 문서 경로는 루트 AGENTS.md에서 연결한다. 보관함은 저장소의 .codex/docs/이며 .codex/docs/index.md에서 문서를 찾는다. 역할 문서는 담당 책임과 실행 방식만 정의한다.
