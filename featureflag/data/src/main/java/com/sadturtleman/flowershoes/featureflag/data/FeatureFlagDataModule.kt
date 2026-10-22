package com.sadturtleman.flowershoes.featureflag.data

import com.sadturtleman.flowershoes.common.di.IoDispatcher
import com.sadturtleman.flowershoes.featureflag.domain.FeatureFlagProvider
import com.sadturtleman.flowershoes.featureflag.domain.FlagStateRecorder
import com.sadturtleman.flowershoes.featureflag.domain.RemoteConfigSource
import com.sadturtleman.flowershoes.featureflag.domain.createFeatureFlagProvider
import org.koin.dsl.module

/** 환경(AppEnvironment)은 BuildConfig 를 아는 앱 모듈이 꽂는다. */
val featureFlagDataModule = module {
    single<RemoteConfigSource> { SampleRemoteConfigSource() }
    single<FlagStateRecorder> { BizLogFlagStateRecorder(get()) }
    single<FeatureFlagProvider> { createFeatureFlagProvider(get(), get(), get(), get(IoDispatcher)) }
}
