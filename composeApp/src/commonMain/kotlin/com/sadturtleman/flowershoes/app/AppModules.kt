package com.sadturtleman.flowershoes.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sadturtleman.flowershoes.core.auth.AuthGateway
import com.sadturtleman.flowershoes.core.auth.LocalOnlyAuthGateway
import com.sadturtleman.flowershoes.core.data.AttachmentStore
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
import com.sadturtleman.flowershoes.feature.calendar.CalendarViewModel
import com.sadturtleman.flowershoes.feature.drawing.DrawingEditorViewModel
import com.sadturtleman.flowershoes.feature.home.HomeViewModel
import com.sadturtleman.flowershoes.feature.letter.LetterViewModel
import com.sadturtleman.flowershoes.feature.memory.MemoryViewModel
import com.sadturtleman.flowershoes.feature.pairing.PairingViewModel
import com.sadturtleman.flowershoes.feature.settings.SettingsViewModel
import com.sadturtleman.flowershoes.core.data.sync.BlobStore
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.data.sync.KeyVault
import com.sadturtleman.flowershoes.core.data.sync.Mailbox
import com.sadturtleman.flowershoes.core.data.sync.PairingSession
import com.sadturtleman.flowershoes.core.data.sync.PairingTransport
import com.sadturtleman.flowershoes.core.data.sync.SyncClient
import com.sadturtleman.flowershoes.feature.onboarding.OnboardingViewModel
import kotlin.time.Clock
import okio.Path
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 서버 쪽 구현 묶음. Firebase 설정이 있는 빌드에서만 플랫폼이 만들어 넘긴다. */
class CloudBackend(val mailbox: Mailbox, val blobs: BlobStore, val pairing: PairingTransport)

/**
 * DI 조립. 플랫폼이 만든 DB · DataStore 를 받아 나머지를 잇는다.
 *
 * [auth] 는 Firebase · 카카오 SDK 가 연결되기 전까지 [LocalOnlyAuthGateway] 다.
 * [cloud] 가 null 이면 혼자 모드만 동작한다(연결 화면이 안내를 보여 준다).
 * [vault] 는 플랫폼 키 보관함(Keystore · Keychain). 붙기 전에는 메모리 보관함이다.
 */
fun kkotsinModules(
    database: AppDatabase,
    settingsStore: DataStore<Preferences>,
    attachmentsRoot: Path,
    auth: AuthGateway = LocalOnlyAuthGateway(),
    cloud: CloudBackend? = null,
    vault: KeyVault = InMemoryKeyVault(),
): List<Module> = listOf(
    module {
        single { database }
        single { DeviceSettings(settingsStore) }
        single { IdentityHolder() }
        single<Identity> { get<IdentityHolder>() }
        single<Clock> { Clock.System }
        single { auth }

        single { SyncWriter(get(), get(), get()) }
        single { AttachmentStore(get(), attachmentsRoot) }
        single { InboxApplier(get(), get()) }
        single { ProfileRepository(get(), get(), get()) }
        single { EventRepository(get(), get(), get(), get()) }
        single { LetterRepository(get(), get(), get(), get()) }
        single { MissYouRepository(get(), get(), get(), get()) }
        single { StatusRepository(get(), get(), get(), get()) }
        single { vault }
        // 서버 설정이 없는 빌드에서는 등록하지 않는다(화면은 getOrNull 로 "설정 전" 안내를 보여 준다).
        if (cloud != null) {
            single { PairingSession(get(), get(), get(), cloud.pairing, get()) }
            single { SyncClient(get(), get(), get(), cloud.mailbox, cloud.blobs, get(), get(), get(), get()) }
        }
    },
    module {
        viewModel { OnboardingViewModel(get(), get(), get()) }
        viewModel { HomeViewModel(get(), get(), get(), get()) }
        viewModel { CalendarViewModel(get(), get()) }
        viewModel { LetterViewModel(get(), get(), get(), get()) }
        viewModel { DrawingEditorViewModel(get(), get()) }
        viewModel { MemoryViewModel(get(), get(), get()) }
        viewModel { PairingViewModel(getOrNull(), getOrNull(), get()) }
        viewModel { SettingsViewModel(get(), get()) }
    },
)
