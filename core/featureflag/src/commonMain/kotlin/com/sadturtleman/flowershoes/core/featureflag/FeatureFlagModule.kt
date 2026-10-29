package com.sadturtleman.flowershoes.core.featureflag

import com.sadturtleman.flowershoes.common.di.IoDispatcher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 제공자 조립.
 *
 * [environment] 는 빌드가 정한다(Android 는 플레이버의 BuildConfig, iOS 는 Info.plist).
 * [remote] 는 Firebase 설정이 있으면 [FirebaseRemoteConfigSource] 를 넘기고, 없으면 null 이라 샘플 값을 읽는다.
 */
fun featureFlagModule(environment: AppEnvironment, remote: () -> RemoteConfigSource?): Module = module {
    single { environment }
    single<RemoteConfigSource> { remote() ?: SampleRemoteConfigSource() }
    single<FlagStateRecorder> { BizLogFlagStateRecorder(get()) }
    single<FeatureFlagProvider> { createFeatureFlagProvider(get(), get(), get(), get(IoDispatcher)) }
}
