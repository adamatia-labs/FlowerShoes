package com.sadturtleman.flowershoes.di

import com.sadturtleman.flowershoes.BuildConfig
import com.sadturtleman.flowershoes.common.util.device.AppBuildVariant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 이 빌드가 어떤 변형인지 알려 주는 곳.
 *
 * :common:util 은 기기와 앱에 대한 거의 모든 것을 런타임에 읽어 낼 수 있지만 빌드 변형만은
 * 못 읽는다 — BuildConfig 는 컴파일 타임 상수이고, 그것을 생성한 모듈의 것이다.
 * :featureflag 의 환경(APP_ENV)이 같은 이유로 같은 길을 지난다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DeviceInfoAppModule {

    @Provides
    @Singleton
    fun provideAppBuildVariant(): AppBuildVariant = AppBuildVariant(
        buildType = BuildConfig.BUILD_TYPE,
        flavor = BuildConfig.FLAVOR,
    )
}
