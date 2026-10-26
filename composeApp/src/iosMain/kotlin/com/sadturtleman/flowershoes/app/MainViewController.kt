package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.databaseBuilder
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController
import kotlinx.coroutines.launch

private var started = false
private var servicesStarted = false
private val appScope = kotlinx.coroutines.MainScope()

/** Swift(KkotsinApp)가 넣는 위젯 새로고침(WidgetCenter.reloadAllTimelines — Kotlin 에서는 부를 수 없다). */
private var widgetReloader: (() -> Unit)? = null
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
                        com.sadturtleman.flowershoes.core.firebase.FirebaseAuthGateway(signInUi, hasBackup = { com.sadturtleman.flowershoes.core.firebase.hasCloudBackup(it) })
                    } else {
                        com.sadturtleman.flowershoes.core.auth.LocalOnlyAuthGateway()
                    },
                    vault = com.sadturtleman.flowershoes.core.data.sync.IosKeyVault(),
                ) + org.koin.dsl.module {
                    single<com.sadturtleman.flowershoes.core.backup.BackupKeySync> {
                        com.sadturtleman.flowershoes.core.backup.ICloudBackupKeySync { get<com.sadturtleman.flowershoes.core.data.Identity>().userId }
                    }
                    single {
                        val temporary = (requireNotNull(support?.path) + "/db/backup-work").toPath()
                        com.sadturtleman.flowershoes.core.backup.BackupManager(
                            storage = if (firebase) com.sadturtleman.flowershoes.core.firebase.IosBackupStorage()
                                else com.sadturtleman.flowershoes.core.backup.FileBackupStorage((requireNotNull(support?.path) + "/db/backup-local").toPath()),
                            source = com.sadturtleman.flowershoes.core.backup.RoomBackupSource(get(), get(), get(), get(), temporary),
                            vault = get(),
                            // Local emulator QA must never put fake-account keys into the user's iCloud.
                            keySync = if (emulatorAuth) null else get(),
                            applier = com.sadturtleman.flowershoes.core.backup.RoomRestoreApplier(get(), get(), get(), get(), temporary,
                                openBackup = { com.sadturtleman.flowershoes.core.database.backupDatabaseBuilder(it).buildKkotsin() },
                                scheduler = { getOrNull() }),
                            appSchemaVersion = com.sadturtleman.flowershoes.core.database.AppDatabase.VERSION,
                        )
                    }
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
                        single<com.sadturtleman.flowershoes.core.data.LifetimeStore> { IosLifetimeStore(purchases, get()) }
                        single<com.sadturtleman.flowershoes.core.data.BookStore> {
                            object : com.sadturtleman.flowershoes.core.data.BookStore, com.sadturtleman.flowershoes.core.data.OneTimeProduct by IosProduct(purchases, "memory_book", "memory_book") {}
                        }
                        single<com.sadturtleman.flowershoes.core.data.BackupPlusStore> {
                            object : com.sadturtleman.flowershoes.core.data.BackupPlusStore, com.sadturtleman.flowershoes.core.data.OneTimeProduct by IosProduct(purchases, "backup_plus", "backup_plus") {}
                        }
                    }
                },
            )
        }
        started = true
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
            androidx.compose.runtime.CompositionLocalProvider(
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
                koin.get(), koin.get(), koin.get(), koin.get(),
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
