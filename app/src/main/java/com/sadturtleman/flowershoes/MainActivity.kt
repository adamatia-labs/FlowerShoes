package com.sadturtleman.flowershoes

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.metrics.performance.JankStats
import com.sadturtleman.flowershoes.ads.AdConsent
import com.sadturtleman.flowershoes.ads.AdmobBanner
import com.sadturtleman.flowershoes.app.DeepLinks
import com.sadturtleman.flowershoes.core.designsystem.LocalAdBanner
import com.sadturtleman.flowershoes.app.KkotsinApp
import com.sadturtleman.flowershoes.jank.domain.JankReporter
import com.sadturtleman.flowershoes.jank.presentation.LocalJankReporter
import com.sadturtleman.flowershoes.jank.presentation.toJankFrame
import com.sadturtleman.flowershoes.tti.domain.TtiRecorder
import com.sadturtleman.flowershoes.tti.presentation.LocalTtiRecorder
import org.koin.android.ext.android.inject

/** FragmentActivity: 기기 인증(BiometricPrompt)이 필요로 한다. ComponentActivity 의 하위 클래스다. */
class MainActivity : FragmentActivity() {

    /** TTI 기록기. 화면 계측은 전부 컴포지션에서 일어나므로 트리에 꽂아 준다. */
    private val ttiRecorder: TtiRecorder by inject()

    /** 프레임 드랍 집계기. 화면 이름과 스크롤 구간을 컴포지션이 알려 주므로 트리에도 꽂는다. */
    private val jankReporter: JankReporter by inject()

    private val lifetime: com.sadturtleman.flowershoes.purchase.RevenueCatLifetimeStore by inject()

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

        // 알림 · 위젯에서 들어온 딥링크(kkotsin://letter/{id} 등)는 셸이 받아 그 화면을 연다.
        intent?.data?.let { DeepLinks.open(it.toString()) }

        // 광고: 동의(필요한 지역만) 뒤 AdMob 을 켠다. 지면은 캘린더 · 휴가 · 설정의 배너 1개씩(Figma 9).
        AdConsent.gather(this)
        // 꽃신 평생 결제 시트는 이 화면 위에 뜬다.
        lifetime.activity = java.lang.ref.WeakReference(this)

        enableEdgeToEdge()
        setContent {
            val lifetimeOwned by lifetime.owned.collectAsState()
            CompositionLocalProvider(
                LocalTtiRecorder provides ttiRecorder,
                LocalJankReporter provides jankReporter,
                // 꽃신 평생이면 광고를 모두 숨긴다(Figma 9).
                LocalAdBanner provides if (lifetimeOwned) null else { slot, modifier -> AdmobBanner(slot, modifier) },
            ) {
                KkotsinApp()
            }
        }
    }

    /** launchMode 가 singleTop 이라 앱 실행 중 들어오는 딥링크는 여기로 온다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { DeepLinks.open(it.toString()) }
    }
}
