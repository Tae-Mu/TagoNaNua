# TAGO 앱 시작 화면 적용 · 2026-11-10

## 확정 시안

HTML launch-concept의 TAGO/한글 카피 마스크 등장, 흐림→선명 캐릭터 등장, 점 세 개와 “곧 만나요”를 적용했다. 자동차 로딩바는 사용자 요청으로 폐기했다.

## 구현

- MainActivity 위에 Compose LaunchScreen을 표시한다. AppNavHost는 뒤에서 준비하며, 인트로 동안 뒤쪽 터치와 접근성 노출을 차단한다. 종료 시 650ms 페이드로 실제 화면에 연결한다.
- SUIT, 기존 TAGO 벡터, 블루 배경과 곡선, 문구별 170ms 시차 등장.
- Blender 표정 포함 108프레임을 투명 animated WebP(768×768, 약 4.5초, 약 1.3MB)로 패키징했다. ImageDecoder/AnimatedImageDrawable을 사용하며 인터넷, WebView, 신규 라이브러리를 추가하지 않았다.
- 캐릭터는 1050ms 동안 94%→100%, 아래 30px→제자리, 투명→불투명으로 등장한다. Android 12 이상에서는 흐림→선명 효과도 적용한다. Android 10/11은 흐림 없이 이동·확대·투명도를 적용한다.
- 디코딩은 IO에서 진행한다. 준비 후 4.9초에 인트로를 종료하며 전체 대기는 최대 7초다. 실패 또는 시스템 애니메이션 비활성화 시 준비 후 300ms에 종료한다. 실제 DB 로딩 진행률을 의미하지 않는다.
- 백그라운드 진입 시 인트로를 종료하고 애니메이션을 정지한다. Activity 재생성 및 기존 Activity 재진입 시 반복하지 않는다.
- Android 12 이상 OS 시작 화면에도 TAGO 로고를 사용한다. 콘텐츠는 시스템 바 안전 영역 안에 배치한다.
- 여행 시작 시의 기존 로고 연출과 여행·정산 데이터 처리는 변경하지 않았다.

## 자산 출처

- 원본: blender/models/2026-11-09-mascot-v2-four-actions.blend.
- 추가 모션 작업본: 로컬 HTML preview/launch-concept/source/tago-launch-expressions.blend.
- 앱 자산: app/src/main/res/drawable-nodpi/tago_launch.webp.
- img2webp로 각 프레임 42/42/41ms, quality 82로 인코딩했다.

## 검증

- assembleDebug / lintDebug 성공. 신규 LaunchScreen에 Lint 이슈 없음.
- TAGO_Tablet(emulator-5556)에 install -r로 기존 데이터를 보존하며 설치했다.
- 콜드 실행에서 투명 캐릭터 및 표정 렌더 확인, 종료 후 실제 홈과 기존 여행 목록 표시 확인.
- 하단 로딩 문구의 시스템 작업표시줄 겹침 보정 후 재확인.
- 시스템 애니메이션 비활성화 시 빠른 홈 진입 및 홈 버튼으로 나갔다 돌아올 때 인트로 미반복 확인. 테스트 설정은 원래 값으로 복구했다.
- 단위/전체 기기 회귀 테스트는 실행하지 않았다. 실차 성능, Android 10/11 실기기, 회전 중 재생성은 별도 검증 대상이다.
