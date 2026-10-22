package com.sadturtleman.flowershoes

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sadturtleman.flowershoes.app.kkotsinAndroidModules
import com.sadturtleman.flowershoes.core.backup.BackupManager
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.IdentityHolder
import com.sadturtleman.flowershoes.core.data.sync.KeyVault
import com.sadturtleman.flowershoes.core.data.sync.SyncScheduler
import com.sadturtleman.flowershoes.core.notification.NotificationSync
import com.sadturtleman.flowershoes.core.widget.WidgetSnapshotPublisher
import com.sadturtleman.flowershoes.featureflag.domain.FeatureFlagProvider
import com.sadturtleman.flowershoes.logging.domain.BizLogger
import com.sadturtleman.flowershoes.notification.AndroidNotificationScheduler
import com.sadturtleman.flowershoes.tti.domain.TtiRecorder
import com.sadturtleman.flowershoes.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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

    override fun onCreate() {
        super<Application>.onCreate()
        // DI 그래프 하나(Koin): 꽃신 공유 앱 + 안드로이드 계측 모듈.
        startKoin {
            androidContext(this@FlowerShoesApplication)
            modules(kkotsinAndroidModules(this@FlowerShoesApplication))
            modules(telemetryModules(this@FlowerShoesApplication))
        }
        appScope.launch {
            // 신원(로컬 id)을 먼저 읽어 둔다 — Repository 가 쓴다.
            get<IdentityHolder>().update(get<DeviceSettings>().ensureIdentity())
            // 기기 키는 연결 · 기기 교체에 필요하다. 처음 실행이면 만들어 Keystore 보관함에 둔다.
            val vault = get<KeyVault>()
            Sodium.init()
            val keys = vault.deviceKeys() ?: DeviceKeys.generate().also { vault.putDeviceKeys(it) }
            if (BuildConfig.DEBUG) android.util.Log.d("Kkotsin", "device key ${keys.signPub.take(4).joinToString("") { "%02x".format(it) }}")
            // 위젯 스냅샷 · 로컬 알림은 DB 가 바뀌면 다시 쓴다.
            get<WidgetSnapshotPublisher>().start(this) { updateAllWidgets(this@FlowerShoesApplication) }
            NotificationSync(get(), get(), get(), get(), AndroidNotificationScheduler(this@FlowerShoesApplication)).start(this)
            // 서버 설정이 있는 빌드에서만 등록돼 있다.
            getKoin().getOrNull<SyncScheduler>()?.start(this)
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
    /**
     * 자동 백업(설계 7-6): 백그라운드로 갈 때, 마지막 백업에서 30분이 지났으면.
     * 실패해도 다음 기회에 다시 한다 — 원본은 이 폰의 DB 다.
     */
    private fun autoBackup() {
        val manager = getKoin().getOrNull<BackupManager>() ?: return
        val prefs = getSharedPreferences("kkotsin_backup", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last", 0L) < 30 * 60 * 1000L) return
        appScope.launch {
            runCatching { manager.backupNow(get<IdentityHolder>().userId) }
                .onSuccess { prefs.edit().putLong("last", now).apply() }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        autoBackup()
        ttiRecorder.destroy()
        // 아직 나가지 못한 이벤트가 끝나기를 기다린 뒤 접는다.
        bizLogger.destroy()
    }
}
