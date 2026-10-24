package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.collectAsState
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
private var widgetsStarted = false

/** Swift(KkotsinApp)가 넣는 위젯 새로고침(WidgetCenter.reloadAllTimelines — Kotlin 에서는 부를 수 없다). */
private var widgetReloader: (() -> Unit)? = null

fun setWidgetReloader(reload: () -> Unit) {
    widgetReloader = reload
}

/** iosApp(SwiftUI)이 부르는 진입점. Koin 은 한 번만 시작한다. */
@OptIn(ExperimentalForeignApi::class)
/**
 * [signInUi]: Swift 의 제공자 화면(카카오 SDK · Sign in with Apple · Google Sign-In). Firebase 가 없으면 쓰지 않는다.
 * [ads]: Swift 의 AdMob 배너. 없으면 광고 지면이 비어 있다.
 * [purchases]: Swift 의 RevenueCat 결제(꽃신 평생). 없으면 "앱 출시와 함께" 안내만.
 */
fun MainViewController(
    signInUi: com.sadturtleman.flowershoes.core.firebase.SocialSignInUi? = null,
    ads: IosAdBanners? = null,
    purchases: IosPurchases? = null,
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
                    auth = if (cloud != null && signInUi != null) {
                        com.sadturtleman.flowershoes.core.firebase.FirebaseAuthGateway(signInUi, hasBackup = { com.sadturtleman.flowershoes.core.firebase.hasCloudBackup(it) })
                    } else {
                        com.sadturtleman.flowershoes.core.auth.LocalOnlyAuthGateway()
                    },
                    vault = com.sadturtleman.flowershoes.core.data.sync.IosKeyVault(),
                ) + org.koin.dsl.module {
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
    return ComposeUIViewController {
        val perks = org.koin.mp.KoinPlatform.getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.SharedPerks>()
        // 내가 샀거나 연결된 상대가 산 꽃신 평생(연결된 동안 함께).
        val owned = perks?.lifetimeOn?.collectAsState(initial = false)?.value ?: false
        // 꽃신 평생이면 광고를 모두 숨긴다(Figma 9).
        if (ads == null || owned) {
            KkotsinApp()
        } else {
            androidx.compose.runtime.CompositionLocalProvider(
                com.sadturtleman.flowershoes.core.designsystem.LocalAdBanner provides { slot, modifier -> IosAdBanner(ads, slot, modifier) },
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
    kotlinx.coroutines.MainScope().launch {
        koin.getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncClient>()?.let { com.sadturtleman.flowershoes.app.ios.mergeNseQueue(it) }
        koin.getOrNull<com.sadturtleman.flowershoes.core.data.sync.SyncScheduler>()?.requestNow()
        // 꽃신 평생 이용권(계정 맞추기 · 다시 읽기).
        runCatching { koin.getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>()?.refresh() }
        // 위젯 스냅샷 발행기(한 번): DB 가 바뀌면 App Group 의 스냅샷을 다시 쓰고 위젯을 새로 그린다.
        if (!widgetsStarted) {
            widgetsStarted = true
            val store = koin.get<com.sadturtleman.flowershoes.core.widget.SnapshotStore>()
            koin.get<com.sadturtleman.flowershoes.core.widget.WidgetSnapshotPublisher>().start(kotlinx.coroutines.MainScope()) {
                com.sadturtleman.flowershoes.app.ios.mirrorWidgetDrawing(store)
                widgetReloader?.invoke()
            }
        }
        com.sadturtleman.flowershoes.app.ios.writeLinkConfig(
            koin.get<com.sadturtleman.flowershoes.core.database.AppDatabase>(),
            koin.get<com.sadturtleman.flowershoes.core.data.Identity>(),
        )
    }
}
