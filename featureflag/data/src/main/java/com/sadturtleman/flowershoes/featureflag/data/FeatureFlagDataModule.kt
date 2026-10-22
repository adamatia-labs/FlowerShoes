package com.sadturtleman.flowershoes.featureflag.data

import com.sadturtleman.flowershoes.common.di.IoDispatcher
import com.sadturtleman.flowershoes.featureflag.domain.AppEnvironment
import com.sadturtleman.flowershoes.featureflag.domain.FeatureFlagProvider
import com.sadturtleman.flowershoes.featureflag.domain.FlagStateRecorder
import com.sadturtleman.flowershoes.featureflag.domain.RemoteConfigSource
import com.sadturtleman.flowershoes.featureflag.domain.createFeatureFlagProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * :featureflag:domain 의 포트를 조립하는 곳.
 *
 * [AppEnvironment] 는 여기서 묶지 않는다 — 어느 환경으로 빌드됐는지는 빌드 플레이버가 아는 일이라
 * app 모듈이 BuildConfig 에서 읽어 바인딩한다.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FeatureFlagDataModule {

    /** Firebase Remote Config 를 붙이면 이 한 줄이 그 구현을 가리키게 된다. */
    @Binds
    @Singleton
    abstract fun bindRemoteConfigSource(impl: SampleRemoteConfigSource): RemoteConfigSource

    @Binds
    @Singleton
    abstract fun bindFlagStateRecorder(impl: BizLogFlagStateRecorder): FlagStateRecorder

    companion object {

        /**
         * 제공자는 도메인이 만든다.
         *
         * 생성자 주입 대신 여기서 조립하는 것은 [IoDispatcher] 한정자가 안드로이드 모듈에 있어
         * 순수 코틀린인 :featureflag:domain 이 참조할 수 없기 때문이다.
         */
        @Provides
        @Singleton
        fun provideFeatureFlagProvider(
            remote: RemoteConfigSource,
            environment: AppEnvironment,
            stateRecorder: FlagStateRecorder,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): FeatureFlagProvider =
            createFeatureFlagProvider(remote, environment, stateRecorder, ioDispatcher)
    }
}
