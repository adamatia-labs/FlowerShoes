# 코드 리뷰 기준

리뷰(사람 · `code-review` 스킬)가 Standards 축에서 확인하는 이 저장소의 규칙. 어긋나면 지적한다.

## 분석 이벤트(planning.md §4 · §9-7)

**정해진 이벤트만, 사용자 입력 글자 금지, 마스킹 없음.**

- 이벤트는 `core/logging` 의 sealed `BizEvent` 에 정의된 것만 남긴다. 부르는 자리에서 이름이나 맵을 짓지 않는다. 새 이벤트는 `BizEvent.kt` 에 한 덩어리를 더하고 `BizEventTest` 의 견본 · `when` 가지를 더한다.
- 이벤트 값은 **화면 이름(`BizView`) · 선택지(앱이 정한 id · enum) · 숫자 · 참거짓**만. 사용자가 입력한 글자(편지 본문 · 제목 · 그림 한 줄 · 닉네임 · 연결 코드 · 문의 내용 · 이메일)는 어떤 모양으로도 싣지 않는다 — 줄이거나 가리거나 해시해서 싣는 것도 안 된다. "입력했는가" 는 참거짓으로, "몇 개인가" 는 숫자로 바꿔 싣는다.
- 그래서 마스킹 · 필터 코드는 만들지 않는다. 막는 곳은 이 리뷰다: `BizEvent` 에 `String` 값이 새로 생기면 그 값이 어디서 오는지(앱이 정한 선택지인지) 확인한다.
- GA4 한도: 이벤트 이름 40자(소문자 · 숫자 · 밑줄), 파라미터 25개(`view_name` 포함), 값 100자. `BizEventTest` 와 `AnalyticsBizLogShooter` 가 지킨다. 플래그처럼 늘어나는 값은 `FlagsResolved.split` 처럼 나눈다.
- 이벤트 이름 · 화면 이름은 수집 뒤 지표의 키다. 한번 나간 이름은 바꾸지 않는다.
- 디버그 빌드는 GA4 로 아무것도 보내지 않는다(BizEvent 는 표준 출력, 자동 수집은 Android `firebase_analytics_collection_enabled` · iOS `FIREBASE_ANALYTICS_COLLECTION_ENABLED` 로 끈다).
- 수집 항목이 바뀌면 개인정보 문서(`core/designsystem` 의 `TermsDoc.PRIVACY`)도 같은 PR 에서 고친다.

## 로컬 DB 스키마(Room)

**스키마를 바꾸면 버전을 올리고 `AutoMigration` 또는 `Migration` + 테스트를 함께 넣는다. 파괴적 폴백 금지.**

- 대상: `core/database` 의 `AppDatabase`(kkotsin.db), `core/tti` 의 `TtiDatabase`(tti.db). 둘 다 출시 기준이 버전 1 이다.
- Entity · `@Database` 의 표 · 열 · 인덱스 · 외래 키가 바뀌면 `VERSION` 을 1 올린다. 같은 PR 에 새 `schemas/<패키지.클래스>/<버전>.json` 을 커밋한다. 옛 버전 JSON 은 지우지 않는다(`MigrationTest` 가 버전마다 있는지 본다).
- 이전 경로: Room 이 알아서 할 수 있는 변경(표 · 열 추가, 기본값 있는 열)은 `@Database(autoMigrations = [AutoMigration(from = n, to = n + 1)])`. 이름 바꾸기 · 지우기는 `AutoMigrationSpec`(`@RenameColumn` · `@DeleteColumn` …). 값을 옮기거나 다시 계산해야 하면 `Migration` 을 써서 `AppDatabase.MIGRATIONS`(`TtiDatabase.MIGRATIONS`)에 더한다. 빌더는 이 목록만 붙인다.
- 테스트: `MigrationTest`(`TtiMigrationTest`) 가 `MigrationTestHelper` 로 옛 버전마다 DB 를 만들어 현재 버전까지 이전하고 스키마를 검증한다. 값을 옮기는 `Migration` 이면 옛 버전에 행을 넣고 이전 뒤 값이 맞는지 보는 테스트를 같은 파일에 더한다.
- `fallbackToDestructiveMigration*` 은 어떤 형태로도 넣지 않는다(다운그레이드용 포함). 경로가 빠졌을 때 표를 지우면 사용자 데이터가 사라진다 — 열리지 않는 편이 낫다.
- CI `Room` 이 `jvmTest` 뒤 스키마 JSON 이 커밋과 다르면 실패한다. 버전을 안 올리고 스키마만 바꾸면 같은 버전 JSON 이 덮어써져서 여기서 걸린다.
