package com.sadturtleman.flowershoes.jank.data

import android.content.Context
import android.content.pm.ApplicationInfo
import com.sadturtleman.flowershoes.jank.domain.JankReport
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 프레임 통계를 어디로 보낼지 정하는 곳.
 *
 * BuildConfig.DEBUG 대신 ApplicationInfo.FLAG_DEBUGGABLE 을 보는 이유는 두 가지다 —
 * 모듈마다 buildConfig 를 켜지 않아도 같은 분기를 쓸 수 있고, 서명만 디버그로 바꿔 돌리는
 * 사내 빌드처럼 정식이 아닌 변형도 자동으로 디버그 쪽으로 흐른다.
 *
 * 고르지 않은 쪽을 만들지 않으려고 [Lazy] 로 받는다.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object JankDataModule {

    @Provides
    @Singleton
    fun provideJankReport(
        @ApplicationContext context: Context,
        logcatReport: Lazy<LogcatJankReport>,
        noOpReport: Lazy<NoOpJankReport>,
    ): JankReport {
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return if (isDebuggable) logcatReport.get() else noOpReport.get()
    }
}
