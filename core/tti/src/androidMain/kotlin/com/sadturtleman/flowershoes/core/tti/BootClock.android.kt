package com.sadturtleman.flowershoes.core.tti

import android.os.SystemClock

/** [SystemClock.elapsedRealtime]: 부팅 기준이고 잠든 시간도 센다. 뒤로 가지 않는다. */
internal actual fun bootElapsedMillis(): Long = SystemClock.elapsedRealtime()
