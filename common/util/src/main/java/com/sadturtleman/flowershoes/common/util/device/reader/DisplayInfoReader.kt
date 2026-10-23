package com.sadturtleman.flowershoes.common.util.device.reader

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.sadturtleman.flowershoes.common.util.device.Orientation
import com.sadturtleman.flowershoes.common.util.device.WindowSize

/**
 * 화면과 설정을 읽는 곳. 구성이 바뀌면 값이 달라지므로 아무것도 캐시하지 않는다.
 *
 * 창 크기를 구하는 길이 API 30 을 경계로 갈린다. 아래쪽에는 창을 직접 물어볼 방법이 없어
 * 컨텍스트가 들고 있는 [android.util.DisplayMetrics] 를 쓴다 — Activity 의 것은 창 크기지만
 * 애플리케이션 컨텍스트의 것은 디스플레이 크기다. 그래서 창을 정확히 재야 하는 자리에서는
 * Activity 를 넘겨 달라고 계약에 적어 두었다.
 */
internal class DisplayInfoReader constructor(
    private val appContext: Context,
) {

    /**
     * 창의 픽셀 크기.
     *
     * API 30+ 의 [WindowManager.getCurrentWindowMetrics] 는 시스템 바를 포함한 창 전체를 준다.
     * 멀티윈도우에서 화면이 아니라 우리 창을 재는 것이 요점이라 이쪽을 먼저 쓴다.
     */
    fun windowSize(context: Context = appContext): WindowSize {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 창에 매여 있지 않은 컨텍스트에서는 거부당할 수 있다. 그때는 아래로 떨어진다.
            val bounds = runCatching {
                context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
            }.getOrNull()
            if (bounds != null) return WindowSize(bounds.width(), bounds.height())
        }
        val metrics = context.resources.displayMetrics
        return WindowSize(metrics.widthPixels, metrics.heightPixels)
    }

    fun densityDpi(context: Context = appContext): Int = context.resources.displayMetrics.densityDpi

    fun density(context: Context = appContext): Float = context.resources.displayMetrics.density

    /** 주사율. 읽지 못하면 0 이다 — 기본값 대입은 부르는 쪽(프레임 예산)이 판단한다. */
    fun refreshRate(context: Context = appContext): Float {
        val display = display(context) ?: return 0f
        return display.mode?.refreshRate ?: display.refreshRate
    }

    fun isDarkMode(context: Context = appContext): Boolean =
        (context.configuration().uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 사용자가 설정한 로케일 전부를 우선순위 그대로. 첫 번째만 보면 다국어 사용자를 놓친다. */
    fun locales(context: Context = appContext): List<String> {
        val locales = context.configuration().locales
        return List(locales.size()) { index -> locales[index].toLanguageTag() }
    }

    fun fontScale(context: Context = appContext): Float = context.configuration().fontScale

    fun orientation(context: Context = appContext): Orientation =
        when (context.configuration().orientation) {
            Configuration.ORIENTATION_PORTRAIT -> Orientation.PORTRAIT
            Configuration.ORIENTATION_LANDSCAPE -> Orientation.LANDSCAPE
            else -> Orientation.UNDEFINED
        }

    private fun Context.configuration(): Configuration = resources.configuration

    /**
     * 이 컨텍스트가 그려지는 디스플레이.
     *
     * API 30+ 의 [Context.getDisplay] 는 Activity 처럼 디스플레이에 매인 컨텍스트에서만 답한다 —
     * 애플리케이션 컨텍스트에 대고 부르면 던진다. 그 경우와 구버전은 기본 디스플레이로 떨어진다.
     */
    private fun display(context: Context): Display? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display }.getOrNull()?.let { return it }
        }
        return context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
    }
}
