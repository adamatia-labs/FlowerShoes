package com.sadturtleman.flowershoes.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sadturtleman.flowershoes.core.auth.AuthGateway
import com.sadturtleman.flowershoes.core.auth.LocalOnlyAuthGateway
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.IdentityHolder
import com.sadturtleman.flowershoes.core.data.InboxApplier
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.StatusRepository
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.feature.home.HomeViewModel
import com.sadturtleman.flowershoes.feature.onboarding.OnboardingViewModel
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * DI 조립. 플랫폼이 만든 DB · DataStore 를 받아 나머지를 잇는다.
 *
 * [auth] 는 Firebase · 카카오 SDK 가 연결되기 전까지 [LocalOnlyAuthGateway] 다.
 */
fun kkotsinModules(
    database: AppDatabase,
    settingsStore: DataStore<Preferences>,
    auth: AuthGateway = LocalOnlyAuthGateway(),
): List<Module> = listOf(
    module {
        single { database }
        single { DeviceSettings(settingsStore) }
        single { IdentityHolder() }
        single<Identity> { get<IdentityHolder>() }
        single<Clock> { Clock.System }
        single { auth }

        single { SyncWriter(get(), get(), get()) }
        single { InboxApplier(get(), get()) }
        single { ProfileRepository(get(), get(), get()) }
        single { EventRepository(get(), get(), get(), get()) }
        single { LetterRepository(get(), get(), get(), get()) }
        single { MissYouRepository(get(), get(), get(), get()) }
        single { StatusRepository(get(), get(), get(), get()) }
    },
    module {
        viewModel { OnboardingViewModel(get(), get(), get()) }
        viewModel { HomeViewModel(get(), get(), get(), get()) }
    },
)
