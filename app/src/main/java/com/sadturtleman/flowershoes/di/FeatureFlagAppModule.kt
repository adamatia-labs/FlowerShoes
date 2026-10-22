package com.sadturtleman.flowershoes.di

import com.sadturtleman.flowershoes.BuildConfig
import com.sadturtleman.flowershoes.featureflag.domain.AppEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 이 빌드가 어느 환경인지 알려 주는 곳.
 *
 * :featureflag 는 환경을 스스로 알 수 없다 — BuildConfig 는 빌드한 모듈의 것이라,
 * 플레이버를 가진 app 만 APP_ENV 를 읽을 수 있다.
 * 여기서 한 번 읽어 그래프에 올리면 :featureflag 는 값만 받아 리모트 키 앞에 붙인다.
 */
@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagAppModule {

    @Provides
    @Singleton
    fun provideAppEnvironment(): AppEnvironment = AppEnvironment.of(BuildConfig.APP_ENV)
}
