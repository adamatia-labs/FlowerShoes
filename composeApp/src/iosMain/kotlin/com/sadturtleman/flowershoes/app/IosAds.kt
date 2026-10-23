package com.sadturtleman.flowershoes.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.sadturtleman.flowershoes.core.designsystem.KkAdSlot
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/** 배너 높이가 정해지면(광고를 받으면) Swift 가 알려 준다. 못 받으면 0. */
fun interface AdBannerHeight {
    fun changed(points: Double)
}

/**
 * Swift(Google Mobile Ads SDK)가 만드는 배너. Kotlin 은 SDK 를 모른다 — 너비를 주면 UIView 를 받아 그 자리에 놓는다.
 * 동의(UMP) · SDK 시작도 Swift 가 한다. 광고를 요청할 수 없으면 빈 뷰를 돌려주고 높이를 알리지 않는다.
 */
interface IosAdBanners {
    fun makeBanner(widthPoints: Double, onHeight: AdBannerHeight): UIView
}

/** 광고 지면 한 칸(Figma 9): Swift 배너를 자리 너비로. 광고가 오기 전에는 높이 0. */
@OptIn(ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun IosAdBanner(ads: IosAdBanners, @Suppress("UNUSED_PARAMETER") slot: KkAdSlot, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth.value.toDouble()
        var height by remember(width) { mutableStateOf(0.0) }
        UIKitView(
            factory = { ads.makeBanner(width) { h -> height = h } },
            modifier = Modifier.fillMaxWidth().height(height.dp),
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
        )
    }
}
