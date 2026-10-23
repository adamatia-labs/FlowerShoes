package com.sadturtleman.flowershoes.tti.data

import android.os.SystemClock
import com.sadturtleman.flowershoes.tti.domain.TtiClock

/**
 * 안드로이드 시계.
 *
 * 구간 길이에 [SystemClock.elapsedRealtime] 을 쓰는 것은 이 시계만 뒤로 가지 않기 때문이다 —
 * 벽시계는 사용자가 바꾸거나 NTP 가 당기면 측정값이 음수로도 나온다.
 * (기기가 잠들어도 계속 흐르므로, 백그라운드로 내려간 화면의 구간은 길게 잡힌다.
 *  그런 기록은 대개 완성되지 않은 채 오래돼 정리된다)
 */
internal class AndroidTtiClock constructor() : TtiClock {

    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()

    override fun wallTimeMillis(): Long = System.currentTimeMillis()
}
