package com.sadturtleman.flowershoes.core.jank

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 집계기 조립. [debug] 빌드는 표준 출력으로, 배포 빌드는 아무 데도 내보내지 않는다.
 *
 * 디버그 여부는 플랫폼이 넘긴다 — Android 는 BuildConfig.DEBUG, iOS 는 Platform.isDebugBinary.
 * 프레임 소스(JankStats · CADisplayLink)는 화면을 가진 쪽이 달고 이 집계기에 프레임을 넣는다.
 */
fun jankModule(debug: Boolean): Module = module {
    single<JankReport> { if (debug) PrintJankReport() else NoOpJankReport() }
    single { JankReporter(get()) }
}
