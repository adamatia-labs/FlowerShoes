package com.sadturtleman.flowershoes

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sadturtleman.flowershoes.common.presentation.ui.image.flowerImageLoader
import com.sadturtleman.flowershoes.featureflag.domain.FeatureFlagProvider
import com.sadturtleman.flowershoes.logging.domain.BizLogger
import com.sadturtleman.flowershoes.tti.domain.TtiRecorder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FlowerShoesApplication :
    Application(),
    SingletonImageLoader.Factory,
    DefaultLifecycleObserver {

    /** TTI 기록기. 앱과 수명을 같이하므로 여는 것도 닫는 것도 여기서 한다. */
    @Inject
    lateinit var ttiRecorder: TtiRecorder

    /** 비즈니스 이벤트 기록기. 이것도 앱과 수명을 같이한다. */
    @Inject
    lateinit var bizLogger: BizLogger

    /** 피처 플래그 · AB 테스트. 첫 화면이 뜨기 전에 값을 받아 둔다. */
    @Inject
    lateinit var featureFlags: FeatureFlagProvider

    /**
     * 앱과 수명을 같이하는 스코프. 플래그 받아 오기 하나만 여기서 돈다.
     *
     * onCreate 를 막지 않는 것은 첫 화면이 늦어지면 그대로 TTI 에 얹히기 때문이다.
     * 값이 오기 전에 읽은 플래그는 기본값이 되고, 그것으로도 앱은 돌아간다.
     */
    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super<Application>.onCreate()
        // 지난 실행이 쏘지 못하고 죽은 기록도 이 시점에 함께 나간다.
        ttiRecorder.init()
        // 이 실행의 사용자 UUID 도 여기서 만들어진다. 로그인 지점에서 makeUUID 로 갈아 끼운다.
        bizLogger.init()
        // 로깅을 먼저 연 뒤에 부른다 — 플래그 배정은 받자마자 그 기록기로 나간다.
        appScope.launch { featureFlags.init() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** 앱이 다시 앞으로 나왔다. [onStop] 에서 접어 둔 기록기를 다시 연다. */
    override fun onStart(owner: LifecycleOwner) {
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

    /** 앱 공용 이미지 로더. 구성은 :common:presentation 이 정한다. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        flowerImageLoader(context)
}
