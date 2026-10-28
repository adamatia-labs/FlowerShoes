# logging · featureflag · jank · tti 의 iOS 대응 수단

이슈 #25 조사 결과. 결정은 하지 않는다 — 결정에 필요한 사실만 모은다.
기준: `develop` @ 8a6cc7b, 조사일 2026-10-29.

## 0. 한 줄 요약

- 네 모듈 모두 **`:app`(Android) 에만 연결**돼 있고, `:composeApp`(iOS 가 띄우는 공유 셸)은 하나도 모른다.
- 화면 쪽 호출처가 **하나도 없다** — `TtiPage` · `JankPage` · `BizLogger.record` · `FeatureFlagProvider.get` 을 부르는 feature 코드가 없다. 지금은 Application/Activity 의 init · destroy 배선과 CompositionLocal 주입만 있다.
- `domain` 은 전부 `kotlin.jvm` 모듈이다. 로직은 플랫폼 중립이지만 JVM 전용 API 몇 개(`java.util.UUID`, `ConcurrentHashMap`, `synchronized`, `javaClass`, `System.currentTimeMillis`) 때문에 그대로는 `commonMain` 에 못 올라간다. 전부 표준 KMP 대체재가 있다.
- iOS 에서 **기기 내 실시간 측정**은 jank=CADisplayLink, tti=Compose `withFrameNanos`(이미 공용) + 단조 시계로 가능. **필드 집계**는 MetricKit(일 단위) · Firebase Performance(GitLive `firebase-perf`) 가 맡는다.
- MetricKit 은 iOS 27 에서 Swift 전용 새 API(`MetricManager` · `MetricResult`)로 바뀌었고, Kotlin/Native 가 부를 수 있는 Obj-C `MX*` 클래스는 iOS 27.2 에서 deprecated 됐다.

## 1. 모듈별 현재 상태

| 모듈 | 하는 일 | 의존 (Android 쪽) | KMP 블로커 |
|---|---|---|---|
| `:logging:domain` (kotlin.jvm) | `BizEvent` sealed 이벤트 목록, `BizLogger` 기록기(쌓지 않고 1건=1전송, IO 직렬 + `Mutex` 로 순서 보장, 실패 삼킴) | coroutines-core | `java.util.UUID`, `synchronized`, `@Volatile`(jvm), `javaClass.simpleName`, `System.currentTimeMillis` |
| `:logging:data` (android lib) | Koin 모듈, `PrintBizLogShooter`(println) | koin-android, `:common:di`(android lib, `IoDispatcher` 한정자) | 코드 자체는 순수 — 모듈 타입과 `:common:di` 만 문제 |
| `:featureflag:domain` (kotlin.jvm) | `FlagKey<T>`(KSerializer 로 타입 보유), `AppEnvironment` dev→qa→prod 폴백, stable/volatile 두 칸 캐시, `RemoteConfigSource` 포트(Firebase RC 모양), `FlagStateRecorder` 포트 | coroutines, serialization-json, `:common:entity`(kotlin.jvm) | `ConcurrentHashMap`, `javaClass`; `:common:entity` 도 jvm 모듈 |
| `:featureflag:data` (android lib) | `SampleRemoteConfigSource`(하드코딩 값, 네트워크 없음), `BizLogFlagStateRecorder` → `BizEvent.FlagsResolved` | koin-android, `:common:di`, `:logging:domain` | 코드 순수 — 모듈 타입만 문제. **실제 Firebase Remote Config 는 아직 붙지 않았다** |
| `:jank:domain` (kotlin.jvm) | `JankReporter`: 화면 버킷 · 스크롤 버킷 · 700ms frozen 단건, 120프레임 이후 jank 비율 5% 초과 시 내보냄 | 없음 | **없음** — JVM API 를 하나도 안 쓴다. 플러그인만 바꾸면 commonMain |
| `:jank:presentation` (android lib) | `JankPage`/`JankScrollWatcher` Composable, `FrameData.toJankFrame()` | `androidx.metrics:metrics-performance`(JankStats), `LocalView`, `PerformanceMetricsState` | JankStats 는 Android 전용 |
| `:jank:data` (android lib) | 디버그 빌드면 `Log.d`, 릴리스면 NoOp | `android.util.Log`, `ApplicationInfo.FLAG_DEBUGGABLE` | Android API |
| `:tti:domain` (kotlin.jvm) | 4구간(VIEW_CREATE · BACKEND · VIEW_BINDING · BIG_PART_LOADING) 영속 기록, 완성 시 shot, 다음 init 에서 회수, 24h 지난 미완성 삭제 | coroutines | `java.util.UUID`, `synchronized`, `@Volatile`, `javaClass` |
| `:tti:data` (android lib) | `RoomTtiRecordStore`, `AndroidTtiClock`(`SystemClock.elapsedRealtime`) | Room 2.8.4(android 설정 + KSP), `androidContext()` | Room 은 이미 KMP 지원(프로젝트의 `:core:database` 가 Room KMP 사용), 시계만 플랫폼별 |
| `:tti:presentation` (android lib) | `TtiPage`/`TtiSpanEffect`/`TtiDrawnEffect`/`TtiEmptySpanEffect` | Compose runtime 만(`withFrameNanos`, CompositionLocal) | **없음** — Compose Multiplatform runtime 으로 그대로 옮겨진다 |

### 배선(실제 호출처)

- `app/src/main/java/.../TelemetryModules.kt`: `ttiDataModule` · `jankDataModule` · `bizLogDataModule` · `featureFlagDataModule` + `AppEnvironment.of(BuildConfig.APP_ENV)` + `PrintTtiShooter`.
- `FlowerShoesApplication.kt`: `ttiRecorder.init()` · `bizLogger.init()` · `featureFlags.init()`(onCreate, onStart), `destroy()`(ProcessLifecycle onStop).
- `MainActivity.kt`: `JankStats.createAndTrack(window)` → `jankReporter.onFrame(...)`, onStop 에서 `onAppBackground()`, `LocalTtiRecorder` · `LocalJankReporter` 주입.
- `composeApp/` · `feature/*` · `core/*`: 참조 0건(`grep` 결과). 즉 **iOS 빌드는 이 모듈들을 전혀 링크하지 않는다**.
- `common:util`(`DeviceInfoProvider` — "프레임 하나에 허락된 시간, :jank 의 판정 기준선") · `common:di` · `common:datastore` 도 android lib 이다.
- 기획서 `docs/product/planning.md` §4: "정해진 이벤트만 기록(`BizEvent` sealed 구조 유지). 사용자가 입력한 글자는 넣지 않는다." — `BizEvent` 구조는 유지 대상.

## 2. commonMain 으로 올릴 수 있는 것 vs 플랫폼별

### 그대로 commonMain (코드 수정 거의 없음)
- `:jank:domain` 전체 (JVM API 0개).
- `:tti:presentation` 전체 (Compose runtime 만 사용, CMP 가 같은 API 제공).
- `:logging:data` 의 `PrintBizLogShooter`, `:featureflag:data` 의 `SampleRemoteConfigSource` · `BizLogFlagStateRecorder`.
- `:jank:presentation` 의 `JankScrollWatcher` 중 `snapshotFlow { isScrollInProgress }` 부분 (`ScrollableState` 는 공용). `PerformanceMetricsState` 태깅만 androidMain.

### 작은 치환 후 commonMain
| JVM API | KMP 대체 |
|---|---|
| `java.util.UUID.randomUUID()` | `kotlin.uuid.Uuid.random()` (composeApp 이 이미 `ExperimentalUuidApi` opt-in) |
| `System.currentTimeMillis()` | `kotlin.time.Clock.System.now().toEpochMilliseconds()` (이미 `ExperimentalTime` opt-in) |
| `synchronized(this)` | kotlinx-atomicfu `SynchronizedObject`/`synchronized`, 또는 `AtomicReference` CAS |
| `@Volatile`(kotlin.jvm) | `kotlin.concurrent.Volatile` |
| `ConcurrentHashMap` | atomicfu 락 + `HashMap`, 또는 `Mutex` 로 감싼 맵 (이미 `fetchMutex` 가 있음) |
| `javaClass.simpleName` | `this::class.simpleName` |
| `limitedParallelism(1)` | 공용 API — 그대로 |

같이 KMP 로 바꿔야 하는 의존: `:common:entity`(jvm → KMP), `:common:di`(`IoDispatcher` 한정자. 공용 `Dispatchers.IO` 는 kotlinx-coroutines 1.7+ 부터 Native 에도 있다).

### 플랫폼별(expect/actual 또는 포트 구현)이 필요한 것
| 포트 | Android (현재) | iOS 후보 |
|---|---|---|
| `TtiClock.elapsedMillis` | `SystemClock.elapsedRealtime()` (부팅 기준, 수면 포함 → **프로세스가 죽어도 DB 에 남긴 값과 비교 가능**) | `clock_gettime(CLOCK_MONOTONIC)` / `mach_continuous_time()` 처럼 부팅 기준 · 수면 포함 시계. `kotlin.time.TimeSource.Monotonic` 의 mark 는 프로세스 밖으로 직렬화할 수 없어 이 설계(영속 span)에 맞지 않는다 |
| `TtiRecordStore` | Room(android) | Room KMP(iOS) — `:core:database` 와 같은 방식(bundled SQLite) |
| 프레임 소스(`JankFrame`) | JankStats `FrameData` | CADisplayLink (§3.1) |
| `JankReport` 디버그 출력 | `android.util.Log` | `println`(NSLog 로 감) 또는 os_log; debuggable 판정은 `Platform.isDebugBinary`(kotlin.native) |
| `RemoteConfigSource` | 샘플 | GitLive `firebase-config` 로 **commonMain 구현 1개** 가능 (§3.3) |
| `AppEnvironment` 주입 | `BuildConfig.APP_ENV` | `Config.xcconfig` → Info.plist → `MainViewController` 인자 (`iosApp/Config.xcconfig.example` 패턴 있음) |
| 수명 신호 init/destroy | `ProcessLifecycleOwner` onStart/onStop | `iosApp/App/KkotsinApp.swift` 의 `scenePhase` — `.active` 에서 이미 `MainViewControllerKt.onForeground()` 호출, `.background` 분기 있음. 여기에 `onBackground()` 하나 추가하면 된다 |

## 3. iOS 대응 수단

### 3.1 jank (프레임 드롭)

**기기 내 실시간 (JankReporter 를 그대로 먹이는 방식)**
- `CADisplayLink`: 디스플레이 주사율에 맞춰 콜백. `timestamp`(직전 프레임 표시 시각), `targetTimestamp`(다음 프레임 표시 시각), `duration`(최대 주사율 기준 간격) 제공. 콜백 간 간격이 `targetTimestamp - timestamp` 보다 크게 벌어지면 놓친 프레임으로 판단할 수 있다 — Apple 문서에 드롭 판정 공식은 없고 이것은 통상적 추론이다. iOS 3.1+.
  https://developer.apple.com/documentation/quartzcore/cadisplaylink
- Compose Multiplatform iOS 자체도 `CADisplayLink` 으로 프레임 클록을 돌리지만(`DisplayLinkListener` · `MetalRedrawer`) 둘 다 `internal` 이라 앱이 프레임 콜백을 받을 공개 API 는 없다. `ComposeUIViewControllerConfiguration` 에도 프레임/성능 관련 옵션이 없다(deprecated `delegate` 만).
  https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/window/DisplayLinkListener.ios.kt
  https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui/src/iosMain/kotlin/androidx/compose/ui/uikit/ComposeUIViewControllerConfiguration.ios.kt
- 따라서 iOS 는 `iosMain` 에서 별도 `CADisplayLink` 을 메인 런루프에 붙여 콜백 간격으로 `JankFrame(isJank, durationMillis, states)` 를 만들어 `JankReporter.onFrame` 에 넣는 형태가 된다. 한계: JankStats 의 `frameDurationUiNanos`(UI 스레드가 그 프레임에 쓴 시간) 같은 값은 없고, **표시 간격**만 안다. 화면 이름 · 스크롤 태그는 `PerformanceMetricsState` 대신 공용 상태(예: `JankReporter` 가 들고 있는 currentPage)로 붙여야 한다.
- 대안: 공용 Compose 에서 `withFrameNanos` 무한 루프로 프레임 간격을 재는 방법도 있으나, 루프가 도는 동안 매 vsync 마다 프레임을 요청하게 되어 정적인 화면에서도 렌더링이 계속 돈다(배터리). 스크롤 구간 한정이면 고려 가능.
- `androidx.metrics:metrics-performance`(JankStats) 1.0.0(2025-10) 은 Android 전용 아티팩트다. https://developer.android.com/jetpack/androidx/releases/metrics

**필드 집계 (일 단위, 기기 밖에서 보는 값)**
- MetricKit 히치 지표: `MXAnimationMetric.scrollHitchTimeRatio` · `hitchTimeRatio`(iOS 14+). **iOS 27.2 에서 deprecated** → 새 Swift API `MetricResult.hitchTime(HitchTimeMetric)` 로 대체.
  https://developer.apple.com/documentation/metrickit/mxanimationmetric
  https://developer.apple.com/documentation/metrickit/metricresult
- 화면별로 쪼개지지 않는다(앱 전체 일 단위). 리포트 전달은 "metric source 당 하루 최대 1회".
  https://developer.apple.com/documentation/metrickit/mxmetricmanager
- Firebase Performance iOS 화면 렌더링 트레이스: UIViewController 의 `viewDidAppear:`~`viewDidDisappear:` 를 한 화면으로 본다. slow = 16ms 초과 프레임이 절반 이상, frozen = 700ms 초과 프레임이 0.1% 이상, **60Hz 가정**(SDK 한계로 명시). 꽃신은 전체가 `ComposeUIViewController` 하나 안에서 그려지므로 Compose 화면별로는 갈리지 않을 것이다(추론 — 문서는 UIKit VC 기준만 설명). 커스텀 속성은 못 단다.
  https://firebase.google.com/docs/perf-mon/screen-traces?platform=ios
- 개발 중 분석: CMP 는 공개 함수 `enableTraceOSLog()` 를 켜면 내부 `trace(...)` 구간을 `os_signpost_interval_begin/end`(subsystem `androidx.compose`) 로 내보낸다 → Instruments 의 Animation Hitches / os_signpost 트랙에서 Compose 구간과 히치를 겹쳐 볼 수 있다.
  https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui-util/src/iosMain/kotlin/androidx/compose/ui/util/Trace.ios.kt
  https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/compose/ui/ui-uikit/src/iosMain/objc/CMPUIKitUtils/CMPUIKitObjcUtils/CMPOSLoggerInterval.m

### 3.2 tti (앱 시작 · 화면 첫 표시)

- **화면별 TTI 는 플랫폼 SDK 에 대응물이 없다** — MetricKit · Firebase 모두 앱 시작만 자동으로 잰다. 화면 4구간 측정은 이 모듈의 공용 로직(`:tti:domain` + `:tti:presentation`)이 iOS 에서도 그대로 해야 한다. `TtiDrawnEffect` 의 `withFrameNanos` 는 CMP iOS 에서 `CADisplayLink` 기반 프레임 클록으로 돈다(위 `DisplayLinkListener`), 즉 "그 프레임이 실려 나간 시점"의 의미가 Android 와 같다.
- 앱 시작(필드):
  - MetricKit `MXAppLaunchMetric`: `histogrammedTimeToFirstDraw` · `histogrammedApplicationResumeTime` · `histogrammedOptimizedTimeToFirstDraw`(prewarm) · `histogrammedExtendedLaunch`. iOS 13+, **27.2 deprecated** → `MetricResult.timeToFirstDraw` 등. 히스토그램 · 일 단위.
    https://developer.apple.com/documentation/metrickit/mxapplaunchmetric
  - Extended launch: `MXMetricManager.extendLaunchMeasurement(forTaskID:)` / `finishExtendedLaunchMeasurement(forTaskID:)` (새 API 는 `MetricManager.trackLaunchTask(id:)`) — "첫 화면 데이터가 다 그려질 때까지" 를 앱 시작에 포함시킬 수 있다.
    https://developer.apple.com/documentation/metrickit/metricmanager
  - Firebase Performance `_app_start`(iOS): 첫 Object 로드 → `UIApplicationDidBecomeActiveNotification` 뒤 첫 런루프 완료.
    https://firebase.google.com/docs/perf-mon/app-start-foreground-background-traces?platform=ios
- 커스텀 구간(필드):
  - MetricKit 커스텀 signpost: `makeLogHandle(category:)` 로 만든 OSLog 에 `mxSignpost(...)` 로 begin/end 를 찍으면 일 단위 `MXSignpostMetric`(횟수 · 지속 히스토그램)으로 집계된다. 저장 개수 제한이 있어 "핵심 구간에만" 쓰라고 명시. 27.2 deprecated → `SignpostIntervalMetric`.
    https://developer.apple.com/documentation/metrickit/mxsignpostmetric
  - Firebase Performance 커스텀 트레이스: `Performance.startTrace(name:)` / `stop()`, 트레이스당 metric 32개 · attribute 5개(이름 32자, 영문/밑줄만). → `TtiShooter` 구현으로 `pageName` 별 트레이스 + 4구간을 metric 으로 넣는 형태가 가능.
    https://firebase.google.com/docs/perf-mon/custom-code-traces?platform=ios
- 개발 중: `os_signpost` + Instruments. 주의 — `os_signpost_interval_begin` 등은 C 매크로라 Kotlin/Native cinterop 에서 바로 부르기 어렵다(추론). CMP 도 이 때문에 Obj-C 래퍼(`CMPOSLogger`)를 두었다. 쓰려면 Swift/Obj-C 쪽 얇은 래퍼를 `iosApp` 에 두고 Kotlin 에서 콜백으로 넘기는 방식이 현실적이다.

### 3.3 featureflag

- **GitLive `dev.gitlive:firebase-config`** 를 쓰면 `RemoteConfigSource` 를 commonMain 구현 하나로 끝낼 수 있다. 공용 API: `fetchAndActivate(): Boolean`, `getKeysByPrefix(prefix)`, `getValue(key)`(→ `asBoolean/asLong/asDouble/asString`), `all`, `settings { minimumFetchInterval }`, `setDefaults`. 포트 주석("`hasKey` 는 getKeysByPrefix, 나머지는 같은 이름의 typed getter")과 1:1 대응.
  https://github.com/GitLiveApp/firebase-kotlin-sdk/blob/master/firebase-config/src/commonMain/kotlin/dev/gitlive/firebase/remoteconfig/FirebaseRemoteConfig.kt
- GitLive README 기준 커버리지: Remote Config 49%, Performance 35%, Analytics 16%, Crashlytics 88%. 최신 2.7.0 — **프로젝트는 2.1.0** (`gradle/libs.versions.toml` 의 `gitliveFirebase`). firebase-config 를 추가하면 기존 firestore/auth/storage/functions 와 버전을 맞춰야 하므로 사실상 GitLive 업그레이드가 함께 따라온다(그리고 2.1 이 BoM 33 에 묶인 이유 — ktx 참조 — 를 다시 확인해야 한다).
  https://github.com/GitLiveApp/firebase-kotlin-sdk
- GitLive 는 Firebase iOS SDK 를 전이 의존으로 넣지 않는다 — iOS 쪽에서 직접 링크해야 한다. 이 프로젝트는 `iosApp/project.yml` 의 SPM `Firebase` 패키지에서 product 를 고르는 방식이므로 `FirebaseRemoteConfig`(필요 시 `FirebasePerformance`, `FirebaseAnalytics`)를 앱 타깃에 추가하면 된다(NSE · 위젯 타깃엔 넣지 않는 기존 규칙 유지).
- 대안: GitLive 없이 `iosMain` 에서 Swift 쪽 `RemoteConfig` 를 감싼 구현을 콜백으로 넘기는 방식(지금 `SocialSignInUi` · `Compressor` 를 Swift 가 구현하는 패턴과 같음). 의존을 늘리지 않는 대신 Android/iOS 두 벌.
- `FlagStateRecorder`(→ `BizEvent.FlagsResolved`)는 플랫폼 무관이라 그대로 공용.

### 3.4 logging 싱크

- 지금 싱크는 `println` 뿐이고 수집 서버도 미정이다. iOS 에서 `println` 은 Xcode 콘솔/표준 출력으로 나간다.
- 후보 싱크(모두 `BizLogShooter` 한 자리만 바꾸면 됨):
  - GitLive `firebase-analytics`: `logEvent(name, parameters)`, `setUserId`, `setConsent` 가 공용에 있다(커버리지 16% 지만 기록에 필요한 핵심은 있음). iOS 는 SPM `FirebaseAnalytics` 링크 필요.
    https://github.com/GitLiveApp/firebase-kotlin-sdk/blob/master/firebase-analytics/src/commonMain/kotlin/dev/gitlive/firebase/analytics/analytics.kt
  - 개발용 os_log: 위와 같은 매크로 문제로 Swift 래퍼가 필요. 단순 확인용이면 `println`(NSLog 경유)으로 충분.
- 수집 백엔드 선택 자체는 별도 조사(`research/analytics-backend` 브랜치)의 범위다.
- 기획서 제약(사용자 입력 글자 금지, sealed 유지)은 싱크와 무관하게 `BizEvent` 에서 지켜진다.

## 4. MetricKit 을 Kotlin 에서 부를 수 있는가

- Kotlin/Native 플랫폼 라이브러리에 `platform.MetricKit` 이 있다(`kotlin-native/platformLibs/src/platform/ios/MetricKit.def`). 즉 Obj-C 클래스 `MXMetricManager` · `MXMetricPayload` 는 `iosMain` 에서 직접 쓸 수 있다.
  https://github.com/JetBrains/kotlin/blob/master/kotlin-native/platformLibs/src/platform/ios/MetricKit.def
- 그런데 iOS 27 의 새 API(`MetricManager`(iOS 27.0+, 인스턴스형, `metricReports` 가 `AsyncSequence`), `MetricResult` enum)는 Swift concurrency 기반이라 Obj-C 헤더로 노출되지 않을 가능성이 높다(문서 선언이 Swift 만 있음 — 추론). Obj-C `MXMetricManager` 는 27.2 에서 deprecated(아직 동작).
- 결론적으로 MetricKit 을 쓰면 **수신은 Swift(`iosApp`)에서 하고, 필요한 숫자만 Kotlin 쪽 포트로 넘기는** 구조가 장기적으로 안전하다. MetricKit 페이로드는 Apple 이 하루 1회 주므로 우리 쪽 실시간 리포터(JankReporter · TtiRecorder)와 역할이 겹치지 않는다 — 필드 검증용 보조 지표다.

## 5. Compose Multiplatform iOS 에서 측정 가능한 범위 (정리)

| 측정 | 가능 여부 | 방법 |
|---|---|---|
| 화면 진입 → 첫 프레임(TTI VIEW_CREATE · VIEW_BINDING) | 가능 | 공용 `withFrameNanos` / CompositionLocal — 코드 그대로 |
| 서버 · 이미지 구간(BACKEND · BIG_PART_LOADING) | 가능 | 공용 `LaunchedEffect` — 코드 그대로 |
| 프레임 간격 · 드롭 | 가능(표시 간격만) | `iosMain` 의 `CADisplayLink`. CMP 내부 프레임 클록은 비공개 |
| 프레임당 UI 스레드 작업 시간(JankStats 의 `frameDurationUiNanos`) | 공개 API 없음 | CMP 렌더러 내부 — 개발 중엔 `enableTraceOSLog()` + Instruments |
| Compose 화면별 Firebase 자동 화면 트레이스 | 안 됨(추론) | VC 하나 = 화면 하나로 잡힘. 커스텀 트레이스로 대신 |
| 앱 시작 시간(필드) | 가능 | MetricKit TTFD · Firebase `_app_start` |
| 스크롤 히치 비율(필드) | 가능(앱 전체 · 일 단위) | MetricKit hitch time |

## 6. 결정할 때 걸리는 것 (열린 질문)

1. 네 모듈을 KMP 모듈(`commonMain` + `androidMain` + `iosMain`)로 바꿀 때 `:common:entity` · `:common:di` 를 같이 KMP 로 옮길지, 아니면 `IoDispatcher` 를 `Dispatchers.IO` 로 직접 받게 할지.
2. iOS jank 를 CADisplayLink 로 직접 잴지, MetricKit/Firebase 의 필드 지표만으로 충분하다고 볼지 (실시간 리포터가 iOS 에서 주는 값은 Android 보다 정보가 적다 — UI 작업 시간 없음).
3. Firebase Remote Config/Performance/Analytics 를 GitLive 로 붙일지(→ 2.1.0 → 2.7.x 업그레이드 동반), Swift 래퍼로 붙일지.
4. TTI 영속 저장소의 iOS 시계를 무엇으로 할지 — 프로세스 재시작 뒤에도 비교 가능한 부팅 기준 시계여야 한다.
5. 무엇보다: 지금 화면 호출처가 0건이므로, iOS 대응 이전에 **어느 화면에 무엇을 달지**가 먼저 정해져야 실제 값이 나온다.
