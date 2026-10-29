package com.sadturtleman.flowershoes.core.tti

/** jvm 은 테스트용이다. 부팅 기준은 아니지만 단조롭다. */
internal actual fun bootElapsedMillis(): Long = System.nanoTime() / 1_000_000L
