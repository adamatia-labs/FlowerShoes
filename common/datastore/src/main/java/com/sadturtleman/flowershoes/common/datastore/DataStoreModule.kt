package com.sadturtleman.flowershoes.common.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sadturtleman.flowershoes.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** 사용자 설정과 기기 설정은 파일을 나눈다 — 기기 설정(설치 ID 등)은 계정이 바뀌어도 남아야 한다. */
val UserPreferences = named("user_preferences")
val DevicePreferences = named("device_preferences")

val dataStoreModule = module {
    single<DataStorage>(UserPreferences) { PreferencesDataStorage(createDataStore(androidContext(), get(IoDispatcher), "user_preferences")) }
    single<DataStorage>(DevicePreferences) { PreferencesDataStorage(createDataStore(androidContext(), get(IoDispatcher), "device_preferences")) }
}

private fun createDataStore(context: Context, ioDispatcher: CoroutineDispatcher, name: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile(name) },
    )
