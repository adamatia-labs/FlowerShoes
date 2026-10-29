package com.sadturtleman.flowershoes.core.tti

import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np

/**
 * Darwin 의 CLOCK_MONOTONIC: 부팅 기준이고 잠든 시간도 센다 — Android 의 elapsedRealtime 과 같은 성질이다.
 * (CLOCK_UPTIME_RAW · mach_absolute_time · systemUptime 은 잠든 동안 멈춘다)
 */
internal actual fun bootElapsedMillis(): Long = (clock_gettime_nsec_np(CLOCK_MONOTONIC.toUInt()) / NANOS_PER_MILLI).toLong()

private const val NANOS_PER_MILLI = 1_000_000uL
