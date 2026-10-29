# FlowerShoes
꽃신 앱

군 복무 중인 사람과 그 연인, 두 사람만 쓰는 커플 앱입니다.
두 폰은 종단간 암호화(E2EE)로 일정 · 편지 · 그림 · 추억을 동기화하고, 전역일 · 진급일 · D-day 를 함께 봅니다.

Kotlin Multiplatform(Android · iOS) · Compose Multiplatform · Koin · Room KMP · Firebase(GitLive) 로 구성됩니다.
설계와 코드가 만나는 지점, 진행 상황은 [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md) 에 있습니다.
목표로 하는 제품 결정은 [docs/product/planning.md](docs/product/planning.md), 화면 · 디자인 기준은 [docs/product/design.md](docs/product/design.md) 에 있습니다.

## 모듈

| 모듈 | 성격 | 하는 일 |
|---|---|---|
| `:app` | Android | Application · `MainActivity` · Glance 위젯 · 로컬 알림 · FCM 수신 · 구독(RevenueCat) · 광고(AdMob) · 계측 DI(`TelemetryModules`) |
| `:composeApp` | KMP | 공통 셸(`App.kt`) · 탭과 덮어 띄우는 화면 상태 · Koin 모듈(`AppModules.kt`) · 딥링크 · iOS 진입점(`MainViewController`) |
| `:feature:*` | KMP · Compose | 온보딩 · 홈 · 캘린더 · 편지 · 그림 · 추억 · 연결 · 설정 화면과 ViewModel |
| `:core:*` | KMP | 도메인 로직 · 저장 · 동기화 · 암호화 · 디자인 시스템 (아래 표) |
| `:common:*` | Android · KMP | DataStore 래퍼 · 디스패처 한정자 · 기기 정보 · 공통 모델 |
| `iosApp/` | iOS | XcodeGen 프로젝트 · 앱 진입점 · 알림 확장(NSE) · 위젯 |
| `firebase/` | 서버 | Firestore 보안 규칙 · Cloud Functions(TypeScript) |

### core

| 모듈 | 하는 일 |
|---|---|
| `model` | 도메인 모델 |
| `military` | 복무 규칙 표 · 전역일 · 진급일 · 호봉 · D-day |
| `sync` | 스트림 · 커서 · 재전송/floor/스냅샷 판단 · LWW |
| `crypto` | 기기 키 · 커플 키 · Op 봉투 · 초대 토큰 · 첫 연결 키 도출 · 기기 교체 MAC |
| `database` | Room KMP 테이블 · DAO · 플랫폼별 빌더 |
| `data` | Repository · 쓰기+SyncOp 트랜잭션 · 동기화 클라이언트 · 연결 · 기기 교체 |
| `drawing-engine` · `drawing-ui` | 그림 문서(TLV) · 간소화 · 리플레이 · 렌더러 |
| `compression` | zstd 압축(그림 문서 · 백업) |
| `media` | 그림 PNG 렌더러 |
| `designsystem` | Figma 토큰 4테마 × 라이트/다크 · 컴포넌트 |
| `auth` · `firebase` | 로그인 계약 · Firestore 전송 어댑터 · 푸시 토큰 |
| `notification` · `widget-bridge` | 알림 계획 · 위젯 스냅샷 |
| `logging` | 비즈니스 이벤트(`BizEvent`) → GA4(GitLive Analytics). 디버그는 Logcat · Xcode 콘솔 `BIZLOG:` |
| `featureflag` | 환경별 피처 플래그 · AB 테스트(`FlagKey`) ← Firebase Remote Config(GitLive) |
| `tti` | 화면 진입 시간(4구간 · Room 저장 · 부팅 기준 시계). 화면 helper 는 Android 만 꽂는다. Logcat `TTI:` |
| `jank` | 프레임 드랍 집계. Android 는 JankStats, iOS 는 CADisplayLink 표시 간격. 디버그는 Logcat · Xcode 콘솔 `JANK:` |

### 의존 방향

- `feature` 는 `core` 만 봅니다. 다른 feature 를 참조하지 않습니다.
- 화면 사이 이동은 `:composeApp` 이 콜백으로 잇습니다.
- DI 는 전부 Koin 입니다. ViewModel 은 `AppModules.kt` 에, Android 전용 구현은 `:app` 에서 등록합니다.

## 새 feature 추가

1. `settings.gradle.kts` 에 `:feature:<이름>` 을 추가합니다. `feature/home/build.gradle.kts` 를 복사해 시작합니다.
2. `src/commonMain` 에 화면(`XxxRoute`)과 ViewModel 을 둡니다.
3. `composeApp/build.gradle.kts` 에 모듈을 넣고 `AppModules.kt` 에 `viewModel { ... }` 를 등록합니다.
4. `App.kt` 에서 연결합니다. 탭이면 `Tab` 에 항목을 더하고, 덮어 띄우는 화면이면 `Overlay` 에 더합니다.

## 빌드

- 환경은 `dev` · `qa` · `prod` 플레이버입니다. `dev` · `qa` 는 applicationId 꼬리가 달라 한 기기에 나란히 깔립니다.
- 키는 `local.properties` 에 둡니다. 비어 있으면 그 기능은 "설정 전"으로 동작합니다.

```properties
KAKAO_NATIVE_APP_KEY=
GOOGLE_WEB_CLIENT_ID=
REVENUECAT_API_KEY=
INVITE_HOST=                      # 초대 링크 · App Links 호스트(Firebase Hosting 사이트). 비면 TODO_HOSTING_SITE.web.app
KAKAO_INVITE_TEMPLATE_ID=         # 카카오 사용자 정의 피드 템플릿(인자 name · token). 비면 OS 공유 시트
FIREBASE_EMULATOR_HOST=10.0.2.2   # 로컬 Firebase 에뮬레이터를 쓸 때만(디버그 빌드)
```

```bash
./gradlew :app:assembleDevDebug
```

core 테스트는 Windows 에서도 `jvmTest` 로 돕니다. iOS 타깃은 macOS 에서만 빌드됩니다.

```bash
./gradlew :core:military:jvmTest :core:sync:jvmTest :core:drawing-engine:jvmTest :core:crypto:jvmTest :core:database:jvmTest :core:tti:jvmTest :core:data:jvmTest :core:notification:jvmTest
```

서버 테스트:

```bash
cd firebase/functions && npm install && npm test
```

로컬 Firebase 에뮬레이터로 두 기기 동기화를 돌리는 방법은 [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md), 콘솔 설정은 [docs/SETUP_CONSOLE.md](docs/SETUP_CONSOLE.md) 를 봅니다.

딥링크(`home` · `inbox` · `letter/<id>` · `calendar/<id>` · `pairing`):

```bash
adb shell am start -a android.intent.action.VIEW -d "kkotsin://home"
```

## 로컬 DB(Room)

`core/database`(kkotsin.db) · `core/tti`(tti.db) 둘 다 출시 기준이 버전 1 입니다. 내보낸 스키마 JSON 은 각 모듈의 `schemas/` 에 커밋합니다.

- **스키마를 바꾸면 버전을 올리고 `AutoMigration` 또는 `Migration` + 테스트를 함께 넣습니다. 파괴적 폴백(`fallbackToDestructiveMigration*`)은 쓰지 않습니다.** 경로가 빠지면 사용자 데이터를 지우는 대신 앱이 열리지 않습니다.
- `MigrationTest` · `TtiMigrationTest` 가 옛 버전마다 현재 버전까지 이전되는지 확인하고, CI `Room` 이 새로 생기거나 바뀐 스키마 JSON 이 커밋되지 않았으면 실패합니다.
- 자세한 규칙은 [docs/agents/code-review-standards.md](docs/agents/code-review-standards.md) 를 봅니다.

## CI

GitHub Actions: `Functions`(함수 단위 · Firestore 통합 테스트), `iOS`(macOS 빌드), `Room`(마이그레이션 테스트 · 스키마 JSON 확인).

## 브랜치

`main` ← `develop` ← `feature/*`. 작업은 `feature/*` 에서 하고 `develop` 으로 PR 을 올립니다.
