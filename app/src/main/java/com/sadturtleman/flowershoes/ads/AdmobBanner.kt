package com.sadturtleman.flowershoes.ads

import android.app.Activity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.sadturtleman.flowershoes.BuildConfig
import com.sadturtleman.flowershoes.core.designsystem.KkAdSlot
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 광고 동의(UMP) → AdMob 초기화. 동의가 필요한 지역(EEA · 영국 등)이면 동의 양식을 먼저 띄우고,
 * 광고를 요청해도 될 때만 SDK 를 켠다. 켜지기 전에는 배너 자리가 비어 있다.
 */
object AdConsent {
    private val started = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    fun gather(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { start(activity) } },
            { start(activity) },
        )
        // 지난 실행에서 이미 동의를 받았으면 갱신을 기다리지 않고 바로 켠다.
        start(activity)
    }

    private fun start(activity: Activity) {
        if (!UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) return
        if (started.getAndSet(true)) return
        val app = activity.applicationContext
        // 초기화는 메인 스레드를 막지 않게.
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(app) { _ready.value = true }
        }
    }
}

/**
 * 광고 지면 한 칸(Figma 9): AdMob 고정 적응형 배너, 자리 너비에 맞춘다.
 * 광고가 오기 전 · 못 받았을 때는 높이 0 — 빈 회색 칸을 남기지 않는다. 화면을 떠나면 멈추고 없앤다.
 */
@Composable
fun AdmobBanner(@Suppress("UNUSED_PARAMETER") slot: KkAdSlot, modifier: Modifier = Modifier) {
    val ready by AdConsent.ready.collectAsState()
    if (!ready) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val width = maxWidth.value.toInt()
        var loaded by remember { mutableStateOf(false) }
        val adView = remember(width) {
            AdView(context).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width))
                adListener = object : AdListener() {
                    override fun onAdLoaded() { loaded = true }
                    override fun onAdFailedToLoad(error: LoadAdError) { loaded = false }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(adView, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                adView.destroy()
            }
        }
        AndroidView({ adView }, Modifier.fillMaxWidth().then(if (loaded) Modifier.wrapContentHeight() else Modifier.height(0.dp)))
    }
}
