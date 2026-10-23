package com.sadturtleman.flowershoes.jank.data

import android.content.pm.ApplicationInfo
import com.sadturtleman.flowershoes.jank.domain.JankReport
import com.sadturtleman.flowershoes.jank.domain.JankReporter
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** 디버그 빌드는 Logcat 으로, 배포 빌드는 아무 데도 내보내지 않는다. */
val jankDataModule = module {
    single<JankReport> {
        val debuggable = (androidContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) LogcatJankReport() else NoOpJankReport()
    }
    single { JankReporter(get()) }
}
