package com.sadturtleman.flowershoes

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sadturtleman.flowershoes.app.auth.AuthKeys
import com.sadturtleman.flowershoes.app.kkotsinAndroidModules
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.IdentityHolder
import com.sadturtleman.flowershoes.core.data.sync.KeyVault
import com.sadturtleman.flowershoes.core.data.sync.SyncScheduler
import com.sadturtleman.flowershoes.core.notification.NotificationSync
import com.sadturtleman.flowershoes.core.widget.WidgetSnapshotPublisher
import com.sadturtleman.flowershoes.core.featureflag.FeatureFlagProvider
import com.sadturtleman.flowershoes.core.logging.BizLogger
import com.sadturtleman.flowershoes.notification.AndroidNotificationScheduler
import com.sadturtleman.flowershoes.tti.domain.TtiRecorder
import com.sadturtleman.flowershoes.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FlowerShoesApplication :
    Application(),
    DefaultLifecycleObserver {

    /** TTI 기록기. 앱과 수명을 같이하므로 여는 것도 닫는 것도 여기서 한다. */
    private val ttiRecorder: TtiRecorder by inject()

    /** 비즈니스 이벤트 기록기. 이것도 앱과 수명을 같이한다. */
    private val bizLogger: BizLogger by inject()

    /** 피처 플래그 · AB 테스트. 첫 화면이 뜨기 전에 값을 받아 둔다. */
    private val featureFlags: FeatureFlagProvider by inject()

    /**
     * 앱과 수명을 같이하는 스코프.
     *
     * onCreate 를 막지 않는 것은 첫 화면이 늦어지면 그대로 TTI 에 얹히기 때문이다.
     * 값이 오기 전에 읽은 플래그는 기본값이 되고, 그것으로도 앱은 돌아간다.
     */
    private val appScope = CoroutineScope(SupervisorJob())
    private val localEmulator get() = BuildConfig.DEBUG && BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank()

    /**
     * 푸시 토큰은 앱을 처음 켤 때 발급되는데 그때는 로그인 전이라 등록이 빠진다(onNewToken 은 다시 오지 않는다).
     * 로그인 상태가 될 때마다 지금 토큰을 users/{uid}.fcmTokens 에 넣는다(arrayUnion 이라 중복 없음).
     */
    private fun registerPushTokenOnSignIn() {
        if (localEmulator || com.google.firebase.FirebaseApp.getApps(this).isEmpty()) return
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser == null) return@addAuthStateListener
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                appScope.launch { runCatching { com.sadturtleman.flowershoes.core.firebase.PushTokenRegistry().register(token) } }
            }
        }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        // 위젯 · 알림 PNG 는 화면과 같은 Compose 렌더러로 그린다(글자 스티커를 재는 글꼴 해석기에 Context 가 필요).
        com.sadturtleman.flowershoes.core.media.DrawingBitmap.install(this)
        // DI 그래프 하나(Koin): 꽃신 공유 앱 + 안드로이드 계측 모듈.
        startKoin {
            androidContext(this@FlowerShoesApplication)
            modules(
                kkotsinAndroidModules(
                    this@FlowerShoesApplication,
                    AuthKeys(BuildConfig.KAKAO_NATIVE_APP_KEY, BuildConfig.GOOGLE_WEB_CLIENT_ID),
                    emulatorHost = if (BuildConfig.DEBUG) BuildConfig.FIREBASE_EMULATOR_HOST else "",
                    devEmailLogin = BuildConfig.DEBUG && BuildConfig.DEV_EMAIL_LOGIN,
                    invite = com.sadturtleman.flowershoes.core.data.sync.InviteConfig(
                        host = BuildConfig.INVITE_HOST,
                        // 카카오 SDK 는 로그인 앱 키로 초기화된다 — 키가 없으면 템플릿도 쓸 수 없다.
                        kakaoTemplateId = BuildConfig.KAKAO_INVITE_TEMPLATE_ID.takeIf { it > 0 && BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank() },
                    ),
                ),
            )
            modules(
                telemetryModules(
                    this@FlowerShoesApplication,
                    firebase = !localEmulator && com.google.firebase.FirebaseApp.getApps(this@FlowerShoesApplication).isNotEmpty(),
                ),
            )
            // 꽃신 평생 결제(RevenueCat). 결제 시트는 MainActivity 가 화면을 넘겨 준다.
            modules(org.koin.dsl.module {
                if (BuildConfig.DEBUG) single { com.sadturtleman.flowershoes.core.data.DevFlags(freeBookPdf = true) }
                if (!localEmulator) {
                    single {
                        com.sadturtleman.flowershoes.purchase.RevenueCatLifetimeStore(
                            this@FlowerShoesApplication, BuildConfig.REVENUECAT_API_KEY, get<IdentityHolder>(), BuildConfig.DEBUG,
                        )
                    }
                    single<com.sadturtleman.flowershoes.core.data.LifetimeStore> { get<com.sadturtleman.flowershoes.purchase.RevenueCatLifetimeStore>() }
                    single<com.sadturtleman.flowershoes.core.data.BookStore> { get<com.sadturtleman.flowershoes.purchase.RevenueCatLifetimeStore>().book }
                }
            })
        }
        appScope.launch {
            // 신원(로컬 id)을 먼저 읽어 둔다 — Repository 가 쓴다.
            get<IdentityHolder>().update(get<DeviceSettings>().ensureIdentity())
            // 꽃신 평생 이용권: 계정을 맞추고 읽어 둔다(광고 숨김).
            getKoin().getOrNull<com.sadturtleman.flowershoes.core.data.LifetimeStore>()?.refresh()
            // 기기 키는 연결 · 기기 교체에 필요하다. 처음 실행이면 만들어 Keystore 보관함에 둔다.
            val vault = get<KeyVault>()
            Sodium.init()
            val keys = vault.deviceKeys() ?: DeviceKeys.generate().also { vault.putDeviceKeys(it) }
            if (BuildConfig.DEBUG) android.util.Log.d("Kkotsin", "device key ${keys.signPub.take(4).joinToString("") { "%02x".format(it) }}")
            // 위젯 스냅샷 · 로컬 알림은 DB 가 바뀌면 다시 쓴다.
            get<WidgetSnapshotPublisher>().start(this, drawingWidgetPlaced = {
                androidx.glance.appwidget.GlanceAppWidgetManager(this@FlowerShoesApplication)
                    .getGlanceIds(com.sadturtleman.flowershoes.widget.DrawingWidget::class.java).isNotEmpty()
            }) { updateAllWidgets(this@FlowerShoesApplication) }
            NotificationSync(get(), get(), get(), get(), AndroidNotificationScheduler(this@FlowerShoesApplication), prefs = get<com.sadturtleman.flowershoes.core.data.DeviceSettings>().notifyPrefs).start(this)
            // 서버 설정이 있는 빌드에서만 등록돼 있다.
            getKoin().getOrNull<SyncScheduler>()?.start(this)
            registerPushTokenOnSignIn()
        }
        // 지난 실행이 쏘지 못하고 죽은 기록도 이 시점에 함께 나간다.
        ttiRecorder.init()
        // 이 실행의 사용자 UUID 도 여기서 만들어진다. 로그인 지점에서 makeUUID 로 갈아 끼운다.
        bizLogger.init()
        // 로깅을 먼저 연 뒤에 부른다 — 플래그 배정은 받자마자 그 기록기로 나간다.
        appScope.launch { featureFlags.init() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** 앱이 다시 앞으로 나왔다. [onStop] 에서 접어 둔 기록기를 다시 열고, 빠진 푸시를 동기화로 메운다. */
    override fun onStart(owner: LifecycleOwner) {
        getKoin().getOrNull<SyncScheduler>()?.requestNow()
        ttiRecorder.init()
        bizLogger.init()
    }

    /**
     * 앱이 백그라운드로 내려갔다.
     *
     * 안드로이드는 프로세스 종료를 알려주지 않으므로 이것이 우리가 받는 마지막 신호다.
     * 여기서 남은 완성 기록을 모두 쏜다. 그마저 못 하고 죽으면 다음 [onCreate] 가 주워 간다.
     */
    override fun onStop(owner: LifecycleOwner) {
        ttiRecorder.destroy()
        // 아직 나가지 못한 이벤트가 끝나기를 기다린 뒤 접는다.
        bizLogger.destroy()
    }
}
