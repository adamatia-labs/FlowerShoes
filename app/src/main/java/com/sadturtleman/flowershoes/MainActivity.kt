package com.sadturtleman.flowershoes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.JankStats
import com.sadturtleman.flowershoes.common.domain.helper.MessageHelper
import com.sadturtleman.flowershoes.common.navigation.NavigationHelper
import com.sadturtleman.flowershoes.common.presentation.helper.LocalMessageHelper
import com.sadturtleman.flowershoes.common.presentation.helper.LocalNavigationHelper
import com.sadturtleman.flowershoes.jank.domain.JankReporter
import com.sadturtleman.flowershoes.jank.presentation.LocalJankReporter
import com.sadturtleman.flowershoes.jank.presentation.toJankFrame
import com.sadturtleman.flowershoes.logging.domain.BizLogger
import com.sadturtleman.flowershoes.navigation.LocalBizLogger
import com.sadturtleman.flowershoes.tti.domain.TtiRecorder
import com.sadturtleman.flowershoes.tti.presentation.LocalTtiRecorder
import com.sadturtleman.flowershoes.deeplink.resolveNewIntentRoute
import com.sadturtleman.flowershoes.app.KkotsinApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * @Singleton 으로 제공되는 단일 인스턴스. Composable 트리에는 [LocalNavigationHelper] 로,
     * ViewModel / UseCase 에는 생성자 주입으로 같은 인스턴스가 전달된다.
     */
    @Inject
    lateinit var navigationHelper: NavigationHelper

    /** 메시지도 같은 단일 인스턴스를 Composable 트리와 ViewModel 양쪽이 공유한다. */
    @Inject
    lateinit var messageHelper: MessageHelper

    /** TTI 기록기. 화면 계측은 전부 컴포지션에서 일어나므로 트리에 꽂아 준다. */
    @Inject
    lateinit var ttiRecorder: TtiRecorder

    /** 비즈니스 이벤트 기록기. 화면 진입은 백스택을 쥔 컴포지션에서 남기므로 트리에 꽂아 준다. */
    @Inject
    lateinit var bizLogger: BizLogger

    /** 프레임 드랍 집계기. 화면 이름과 스크롤 구간을 컴포지션이 알려 주므로 트리에도 꽂는다. */
    @Inject
    lateinit var jankReporter: JankReporter

    /**
     * JankStats 는 DecorView 가 생긴 뒤에만 만들 수 있어 [onCreate] 에서 바로 만들지 않는다.
     * 아래 lifecycle 관찰자가 처음 [onResume] 때 한 번 만들고, 그 뒤로는 껐다 켠다.
     */
    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 프레임 계측은 화면이 앞에 있는 동안만 돈다. 뒤로 내려간 뒤의 프레임은 사용자가 보지 않는다.
        // 콜백은 메인 스레드에서 불리므로 여기서 무거운 일을 하면 그것이 다시 프레임을 놓치게 만든다.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (jankStats == null) {
                    jankStats = JankStats.createAndTrack(window) { frameData ->
                        jankReporter.onFrame(frameData.toJankFrame())
                    }
                }
                jankStats?.isTrackingEnabled = true
            }

            override fun onPause(owner: LifecycleOwner) {
                jankStats?.isTrackingEnabled = false
            }

            /** 안드로이드는 프로세스 종료를 알려주지 않으므로 여기서 남은 통계를 비운다. */
            override fun onStop(owner: LifecycleOwner) {
                jankReporter.onAppBackground()
            }
        })

        // 예약 편지 · 일정 전날 알림을 띄우려면 Android 13+ 에서 권한이 필요하다.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(
                LocalNavigationHelper provides navigationHelper,
                LocalMessageHelper provides messageHelper,
                LocalTtiRecorder provides ttiRecorder,
                LocalBizLogger provides bizLogger,
                LocalJankReporter provides jankReporter,
            ) {
                // 화면은 공유 앱 셸이 그린다. 딥링크 시작 화면(resolveStartStack)은 셸에 라우팅이 붙으면 넘긴다.
                KkotsinApp()
            }
        }
    }

    /**
     * launchMode 가 singleTop 이므로, 앱 실행 중 들어오는 새 deep-link 는 여기로 온다.
     * URI 를 NavRoute 로 변환만 하고 실제 dispatch 는 NavigationHelper 통합 플로우가 담당한다.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val uri = intent.data ?: return
        val route = resolveNewIntentRoute(uri) ?: return
        navigationHelper.navigateByRoute(route)
    }
}
