@file:OptIn(ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.core.jank

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CADisplayLink
import platform.darwin.NSObject

/**
 * iOS 의 프레임 소스. 메인 런루프에 CADisplayLink 를 달아 표시 간격을 [JankReporter] 에 넣는다.
 *
 * Compose 가 쓰는 디스플레이 링크는 내부 API 라 붙을 수 없어 따로 하나 단다. 아는 것은 표시 간격뿐이다 —
 * JankStats 처럼 UI 스레드가 그 프레임에 쓴 시간은 공개 API 로 얻을 수 없다.
 * 화면이 그리지 않는 수직 동기도 한 장으로 세므로, 같은 화면이라도 Android 보다 비율이 낮게 나온다
 * (분모가 크다). 두 플랫폼의 비율을 나란히 비교하지 않는다.
 *
 * 앱이 앞에 있는 동안만 돈다([start] · [stop]). 내려가 있던 시간이 한 간격으로 잡히면 얼어붙은 프레임으로 보이므로
 * 다시 켤 때 직전 시각을 버린다. 콜백은 메인 스레드에서 오고, 집계기도 메인 스레드 전제라 잠금이 없다.
 */
class DisplayLinkJankSource(private val reporter: JankReporter) {

    private val ticker = Ticker(::onTick)
    private var link: CADisplayLink? = null

    /** 직전 콜백의 표시 시각 · 예고된 다음 시각(초). 켤 때마다 비운다. */
    private var lastTimestamp: Double? = null
    private var lastTargetTimestamp: Double = 0.0

    fun start() {
        lastTimestamp = null
        val current = link ?: CADisplayLink.displayLinkWithTarget(ticker, NSSelectorFromString("tick:")).also {
            it.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
            link = it
        }
        current.paused = false
    }

    fun stop() {
        link?.paused = true
        lastTimestamp = null
    }

    private fun onTick(link: CADisplayLink) {
        val previous = lastTimestamp
        if (previous != null) {
            reporter.onFrame(
                displayIntervalFrame(
                    intervalMillis = (link.timestamp - previous) * MILLIS_PER_SECOND,
                    expectedIntervalMillis = (lastTargetTimestamp - previous) * MILLIS_PER_SECOND,
                ),
            )
        }
        lastTimestamp = link.timestamp
        lastTargetTimestamp = link.targetTimestamp
    }

    /** CADisplayLink 는 Objective-C 셀렉터로만 부른다. 그 자리를 받는 얇은 NSObject. */
    @OptIn(BetaInteropApi::class)
    private class Ticker(private val onTick: (CADisplayLink) -> Unit) : NSObject() {
        @ObjCAction
        fun tick(link: CADisplayLink) = onTick(link)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
