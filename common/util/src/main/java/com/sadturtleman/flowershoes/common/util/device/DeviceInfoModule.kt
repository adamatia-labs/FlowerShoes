package com.sadturtleman.flowershoes.common.util.device

import com.sadturtleman.flowershoes.common.datastore.DevicePreferences
import com.sadturtleman.flowershoes.common.util.device.reader.DeviceStateReader
import com.sadturtleman.flowershoes.common.util.device.reader.DisplayInfoReader
import com.sadturtleman.flowershoes.common.util.device.reader.InstallationIdStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** 기기 정보. 빌드 변형([AppBuildVariant])은 BuildConfig 를 아는 앱 모듈이 따로 꽂는다. */
val deviceInfoModule = module {
    single { DisplayInfoReader(androidContext()) }
    single { DeviceStateReader(androidContext()) }
    single { InstallationIdStore(get(DevicePreferences)) }
    single<DeviceInfoProvider> { AndroidDeviceInfoProvider(androidContext(), get(), get(), get(), get()) }
}
