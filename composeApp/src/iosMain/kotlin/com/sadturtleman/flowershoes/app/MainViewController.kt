package com.sadturtleman.flowershoes.app

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

/** iosApp(SwiftUI)이 부르는 진입점. Koin 은 한 번만 시작한다. */
@OptIn(ExperimentalForeignApi::class)
/**
 * [signInUi]: Swift 의 제공자 화면(카카오 SDK · Sign in with Apple · Google Sign-In). Firebase 가 없으면 쓰지 않는다.
 * [ads]: Swift 의 AdMob 배너. 없으면 광고 지면이 비어 있다.
 */
fun MainViewController(
    signInUi: com.sadturtleman.flowershoes.core.firebase.SocialSignInUi? = null,
    ads: IosAdBanners? = null,
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
                },
            )
        }
        started = true
    }
    return ComposeUIViewController {
        if (ads == null) {
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
        com.sadturtleman.flowershoes.app.ios.writeLinkConfig(
            koin.get<com.sadturtleman.flowershoes.core.database.AppDatabase>(),
            koin.get<com.sadturtleman.flowershoes.core.data.Identity>(),
        )
    }
}
