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
| `:common:*` | Android · JVM | DataStore 래퍼 · 디스패처 한정자 · 기기 정보 · 공통 모델 |
| `:tti:*` | 계측 | 화면 진입 시간(TTI). Logcat `TTI:` |
| `:jank:*` | 계측 | 프레임 드랍. Logcat `JANK` |
| `:logging:*` | 계측 | 비즈니스 이벤트(`BizEvent`). Logcat `BIZLOG:` |
| `:featureflag:*` | 설정 | 환경별 피처 플래그 · AB 테스트(`FlagKey`) |
| `iosApp/` | iOS | XcodeGen 프로젝트 · 앱 진입점 · 알림 확장(NSE) · 위젯 |
| `firebase/` | 서버 | Firestore 보안 규칙 · Cloud Functions(TypeScript) |

### core

| 모듈 | 하는 일 |
|---|---|
| `model` | 도메인 모델 |
| `military` | 복무 규칙 표 · 전역일 · 진급일 · 호봉 · D-day |
| `sync` | 스트림 · 커서 · 재전송/floor/스냅샷 판단 · LWW |
| `crypto` | 기기 키 · 커플 키 · Op 봉투 · SAS · 기기 교체 MAC · 복구 코드 |
| `database` | Room KMP 테이블 · DAO · 플랫폼별 빌더 |
| `data` | Repository · 쓰기+SyncOp 트랜잭션 · 동기화 클라이언트 · 연결 · 기기 교체 |
| `drawing-engine` · `drawing-ui` | 그림 문서(TLV) · 간소화 · 리플레이 · 렌더러 |
| `compression` | zstd 압축(그림 문서 · 백업) |
| `backup` | 암호화 백업 엔진 · 복구 코드 keywrap |
| `media` | 그림 PNG 렌더러 |
| `designsystem` | Figma 토큰 4테마 × 라이트/다크 · 컴포넌트 |
| `auth` · `firebase` | 로그인 계약 · Firestore 전송 어댑터 · 푸시 토큰 |
| `notification` · `widget-bridge` | 알림 계획 · 위젯 스냅샷 |

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
FIREBASE_EMULATOR_HOST=10.0.2.2   # 로컬 Firebase 에뮬레이터를 쓸 때만(디버그 빌드)
```

```bash
./gradlew :app:assembleDevDebug
```

core 테스트는 Windows 에서도 `jvmTest` 로 돕니다. iOS 타깃은 macOS 에서만 빌드됩니다.

```bash
./gradlew :core:military:jvmTest :core:sync:jvmTest :core:drawing-engine:jvmTest :core:crypto:jvmTest :core:database:jvmTest :core:data:jvmTest :core:notification:jvmTest :core:backup:jvmTest
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

## CI

GitHub Actions: `Functions`(함수 단위 · Firestore 통합 테스트), `iOS`(macOS 빌드).

## 브랜치

`main` ← `develop` ← `feature/*`. 작업은 `feature/*` 에서 하고 `develop` 으로 PR 을 올립니다.
