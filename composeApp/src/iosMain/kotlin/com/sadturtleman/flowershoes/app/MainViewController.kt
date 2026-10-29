package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.data.logged
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.databaseBuilder
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController
import kotlinx.coroutines.launch

private var started = false
private var servicesStarted = false
private val appScope = kotlinx.coroutines.MainScope()

/** Swift(KkotsinApp)가 넣는 위젯 새로고침(WidgetCenter.reloadAllTimelines — Kotlin 에서는 부를 수 없다). */
private var widgetReloader: (() -> Unit)? = null

/** 프레임 드랍 계측의 프레임 소스(CADisplayLink). Koin 이 선 뒤에 만들고, 앞에 있는 동안만 돈다. */
private var jankSource: com.sadturtleman.flowershoes.core.jank.DisplayLinkJankSource? = null
private var notificationRegistration: (() -> Unit)? = null

fun setNotificationRegistration(register: () -> Unit) { notificationRegistration = register }
fun setWidgetEndpoint(endpoint: String?) { com.sadturtleman.flowershoes.app.ios.widgetSendEndpoint = endpoint }

fun onPushToken(token: String) {
    appScope.launch {
        runCatching { com.sadturtleman.flowershoes.core.firebase.PushTokenRegistry().register(token) }
    }
}

fun onRemoteNotification(done: () -> Unit) {
    appScope.launch {
        try {
            val koin = org.koin.mp.KoinPlatform.getKoinOrNull() ?: return@launch
            kotlinx.coroutines.withTimeoutOrNull(20_000) {
                koin.getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncClient>()?.let { com.sadturtleman.flowershoes.app.ios.mergeNseQueue(it) }
                koin.getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncScheduler>()?.runOnce()
            }
        } finally { done() }
    }
}

fun setWidgetReloader(reload: () -> Unit) {
    widgetReloader = reload
}

/** iosApp(SwiftUI)이 부르는 진입점. Koin 은 한 번만 시작한다. */
@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
/**
 * [signInUi]: Swift 의 제공자 화면(카카오 SDK · Sign in with Apple · Google Sign-In). Firebase 가 없으면 쓰지 않는다.
 * [ads]: Swift 의 AdMob 배너. 없으면 광고 지면이 비어 있다.
 * [purchases]: Swift 의 RevenueCat 결제(꽃신 평생). 없으면 "앱 출시와 함께" 안내만.
 */
fun MainViewController(
    signInUi: com.sadturtleman.flowershoes.core.firebase.SocialSignInUi? = null,
    ads: IosAdBanners? = null,
    purchases: IosPurchases? = null,
    emulatorAuth: Boolean = false,
): UIViewController {
    if (!started) {
        val support = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        val settingsPath = requireNotNull(support?.path) + "/kkotsin_device.preferences_pb"
        // Firebase 는 Swift 쪽(KkotsinApp)이 GoogleService-Info.plist 가 있을 때만 configure 한다.
        val firebase = com.sadturtleman.flowershoes.core.firebase.firebaseConfigured()
        // 디버그는 GA4 자동 수집도 끈다(Info.plist 값과 함께).
        if (firebase && kotlin.native.Platform.isDebugBinary) com.sadturtleman.flowershoes.core.logging.disableFirebaseAnalyticsCollection()
        val cloud = if (firebase) {
            val t = com.sadturtleman.flowershoes.core.firebase.FirestoreTransport()
            CloudBackend(
                mailbox = t, blobs = t, pairing = t,
                rotation = com.sadturtleman.flowershoes.core.firebase.FirestoreRotation(),
                account = com.sadturtleman.flowershoes.core.firebase.FunctionsAccount(),
                relink = com.sadturtleman.flowershoes.core.firebase.FirestoreRelink(),
                notifyPrefs = com.sadturtleman.flowershoes.core.firebase.FirestoreNotifyPrefs(),
                inquiry = com.sadturtleman.flowershoes.core.firebase.FirestoreInquiry(),
            )
        } else null
        val group = com.sadturtleman.flowershoes.app.ios.appGroupRoot()
        startKoin {
            modules(
                kkotsinModules(
                    database = databaseBuilder().buildKkotsin(),
                    settingsStore = PreferenceDataStoreFactory.createWithPath { settingsPath.toPath() },
                    attachmentsRoot = (requireNotNull(support?.path) + "/db/attachments").toPath(),
                    cloud = cloud,
                    auth = if (cloud != null && emulatorAuth && kotlin.native.Platform.isDebugBinary) {
                        EmulatorAuthGateway()
                    } else if (cloud != null && signInUi != null) {
                        com.sadturtleman.flowershoes.core.firebase.FirebaseAuthGateway(signInUi)
                    } else {
                        com.sadturtleman.flowershoes.core.auth.LocalOnlyAuthGateway()
                    },
                    vault = com.sadturtleman.flowershoes.core.data.sync.IosKeyVault(),
                ) + telemetryModules(firebase = firebase && !emulatorAuth) + org.koin.dsl.module {
                    if (kotlin.native.Platform.isDebugBinary) {
                        single { com.sadturtleman.flowershoes.core.data.DevFlags(freeBookPdf = true) }
                    }
                    single<com.sadturtleman.flowershoes.feature.settings.PlatformActions> { com.sadturtleman.flowershoes.app.ios.IosPlatformActions() }
                    // 위젯 스냅샷은 App Group 에(위젯 확장이 읽는다).
                    single { com.sadturtleman.flowershoes.core.widget.SnapshotStore(group / "widget/snapshot.json") }
                    single {
                        com.sadturtleman.flowershoes.core.widget.WidgetSnapshotPublisher(
                            get(), get(), get(), get(), get(), get(), get(), get(),
                            (requireNotNull(support?.path) + "/db/attachments").toPath(), get(),
                        )
                    }
                    if (purchases != null) {
                        // 결제 시작 · 완료 이벤트는 스토어를 감싸 남긴다(PurchaseLogging).
                        single<com.sadturtleman.flowershoes.core.data.LifetimeStore> { IosLifetimeStore(purchases, get()).logged(get<com.sadturtleman.flowershoes.core.logging.BizLogger>().purchaseRecorder(com.sadturtleman.flowershoes.core.logging.PurchaseProduct.LIFETIME)) }
                        single<com.sadturtleman.flowershoes.core.data.BookStore> {
                            object : com.sadturtleman.flowershoes.core.data.BookStore, com.sadturtleman.flowershoes.core.data.OneTimeProduct by IosProduct(purchases, "memory_book", "memory_book") {}
                                .logged(get<com.sadturtleman.flowershoes.core.logging.BizLogger>().purchaseRecorder(com.sadturtleman.flowershoes.core.logging.PurchaseProduct.MEMORY_BOOK))
                        }
                    }
                },
            )
        }
        started = true
        // 이 실행의 사용자 UUID 가 여기서 만들어진다. 로깅을 먼저 연 뒤에 플래그를 받는다 — 배정은 받자마자 그 기록기로 나간다.
        val koin = org.koin.mp.KoinPlatform.getKoin()
        koin.get<com.sadturtleman.flowershoes.core.logging.BizLogger>().init()
        appScope.launch { koin.get<com.sadturtleman.flowershoes.core.featureflag.FeatureFlagProvider>().init() }
        // 프레임 계측은 디버그 빌드만 돈다 — 릴리스는 통계를 보낼 곳이 없는데 디스플레이 링크는 매 수직 동기마다 메인 스레드를 깨운다.
        if (kotlin.native.Platform.isDebugBinary) {
            jankSource = com.sadturtleman.flowershoes.core.jank.DisplayLinkJankSource(koin.get())
        }
    }
    return ComposeUIViewController(configure = {
        // 화면의 imePadding/스크롤이 키보드를 처리한다. 루트 전체를 밀면 상단 버튼이 상태바와 겹친다.
        onFocusBehavior = androidx.compose.ui.uikit.OnFocusBehavior.DoNothing
    }) {
        val perks = org.koin.mp.KoinPlatform.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()
        // 내가 샀거나 연결된 상대가 산 꽃신 평생(연결된 동안 함께).
        val owned = perks?.lifetimeOn?.collectAsState(initial = null)?.value
        // 저장된 상대 혜택과 내 구매 상태를 읽기 전에 광고를 만들지 않는다.
        val account = org.koin.mp.KoinPlatform.getKoin().get<com.sadturtleman.flowershoes.core.data.DeviceSettings>()
            .state.collectAsState(initial = null).value
        val purchasesReady = androidx.compose.runtime.produceState(false, account?.userId) {
            value = false
            account?.takeIf { it.userId.isNotBlank() }?.let { current ->
                val koin = org.koin.mp.KoinPlatform.getKoin()
                koin.get<com.sadturtleman.flowershoes.core.data.IdentityHolder>().update(current)
                runCatching { koin.getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>()?.refresh() }
                value = true
            }
        }.value
        // 꽃신 평생이면 광고를 모두 숨긴다(Figma 9).
        Box(Modifier.fillMaxSize().imePadding()) {
            // 구매/공유 혜택이 바뀌어도 앱의 composition 위치와 내비게이션 상태를 유지한다.
            // 화면 이름 · 스크롤 구간은 컴포지션이 알려 주므로 집계기를 트리에 꽂는다.
            // TTI 기록기는 조립도 하지 않는다 — 화면별 TTI 는 Android 만 잰다(iOS 는 MetricKit · Firebase 의 앱 시작 지표).
            // 공유 화면의 TtiPage 등은 기록기가 없으면 아무 일도 하지 않는다.
            androidx.compose.runtime.CompositionLocalProvider(
                com.sadturtleman.flowershoes.core.jank.LocalJankReporter provides
                    org.koin.mp.KoinPlatform.getKoin().get<com.sadturtleman.flowershoes.core.jank.JankReporter>(),
                com.sadturtleman.flowershoes.core.designsystem.LocalAdBanner provides { slot, modifier ->
                    if (ads != null && owned == false && purchasesReady) IosAdBanner(ads, slot, modifier)
                },
            ) { KkotsinApp() }
        }
    }
}

/**
 * iosApp 이 앱이 앞으로 나올 때 부른다: NSE 대기열 합치기 → 동기화 → 위젯 연결 정보 갱신.
 * 위젯 스냅샷 · 로컬 알림 발행기는 첫 화면이 뜬 뒤 한 번 시작한다.
 */
fun onForeground() {
    val koin = org.koin.mp.KoinPlatform.getKoinOrNull() ?: return
    // onBackground 에서 접어 둔 기록기를 다시 열고, 프레임 계측을 다시 켠다.
    koin.get<com.sadturtleman.flowershoes.core.logging.BizLogger>().init()
    jankSource?.start()
    appScope.launch {
        if (!servicesStarted) {
            servicesStarted = true
            appScope.launch {
                koin.get<com.sadturtleman.flowershoes.core.data.DeviceSettings>().state.collect { state ->
                    if (state.onboarded) notificationRegistration?.invoke()
                }
            }
            koin.get<com.sadturtleman.flowershoes.core.data.IdentityHolder>().update(
                koin.get<com.sadturtleman.flowershoes.core.data.DeviceSettings>().ensureIdentity(),
            )
            koin.getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncScheduler>()?.start(appScope)
            com.sadturtleman.flowershoes.core.notification.NotificationSync(
                koin.get(), koin.get(), koin.get(),
                com.sadturtleman.flowershoes.app.ios.IosNotificationScheduler(),
                prefs = koin.get<com.sadturtleman.flowershoes.core.data.DeviceSettings>().notifyPrefs,
            ).start(appScope)
            val store = koin.get<com.sadturtleman.flowershoes.core.widget.SnapshotStore>()
            koin.get<com.sadturtleman.flowershoes.core.widget.WidgetSnapshotPublisher>().start(appScope) {
                com.sadturtleman.flowershoes.app.ios.mirrorWidgetDrawing(store)
                widgetReloader?.invoke()
                appScope.launch {
                    com.sadturtleman.flowershoes.app.ios.writeLinkConfig(
                        koin.get(), koin.get(), vault = koin.get(),
                    )
                }
            }
        }
        koin.getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncClient>()?.let {
            com.sadturtleman.flowershoes.app.ios.mergeNseQueue(it)
            com.sadturtleman.flowershoes.app.ios.mergeWidgetQueue(it)
        }
        koin.getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncScheduler>()?.requestNow()
        // 꽃신 평생 이용권(계정 맞추기 · 다시 읽기).
        runCatching { koin.getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>()?.refresh() }
        com.sadturtleman.flowershoes.app.ios.writeLinkConfig(
            koin.get<com.sadturtleman.flowershoes.core.database.AppDatabase>(),
            koin.get<com.sadturtleman.flowershoes.core.data.Identity>(),
            vault = koin.get(),
        )
    }
}

/**
 * iosApp 이 앱이 백그라운드로 내려갈 때 부른다. iOS 도 종료를 알려주지 않으므로 이것이 마지막 신호다.
 * 아직 나가지 못한 이벤트를 보내고 기록기를 접는다. 프레임 계측은 멈추고 남은 통계를 비운다.
 */
fun onBackground() {
    val koin = org.koin.mp.KoinPlatform.getKoinOrNull() ?: return
    koin.get<com.sadturtleman.flowershoes.core.logging.BizLogger>().destroy()
    jankSource?.stop()
    koin.get<com.sadturtleman.flowershoes.core.jank.JankReporter>().onAppBackground()
}

/**
 * 로깅 · 피처 플래그 · 프레임 드랍. [firebase] 는 GoogleService-Info.plist 로 Firebase 가 켜졌고 로컬 에뮬레이터가 아니다.
 * 디버그 빌드의 기록은 GA4 에 보내지 않고 표준 출력(Xcode 콘솔)으로 흘린다.
 * 환경(dev · qa · prod)은 Info.plist 의 KkotsinAppEnv(Config.xcconfig 의 APP_ENV)다. 비어 있으면 prod.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun telemetryModules(firebase: Boolean): List<org.koin.core.module.Module> = listOf(
    com.sadturtleman.flowershoes.common.di.coroutineModule,
    com.sadturtleman.flowershoes.core.logging.bizLogModule {
        if (firebase && !kotlin.native.Platform.isDebugBinary) com.sadturtleman.flowershoes.core.logging.firebaseAnalyticsSink() else null
    },
    com.sadturtleman.flowershoes.core.featureflag.featureFlagModule(
        com.sadturtleman.flowershoes.core.featureflag.AppEnvironment.of(
            NSBundle.mainBundle.objectForInfoDictionaryKey("KkotsinAppEnv") as? String ?: "",
        ),
    ) { if (firebase) com.sadturtleman.flowershoes.core.featureflag.FirebaseRemoteConfigSource() else null },
    // 디버그 빌드만 프레임 통계를 Xcode 콘솔로 흘린다.
    com.sadturtleman.flowershoes.core.jank.jankModule(debug = kotlin.native.Platform.isDebugBinary),
)
