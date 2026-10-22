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
import com.sadturtleman.flowershoes.feature.settings.BackupViewModel
import com.sadturtleman.flowershoes.core.data.sync.BlobStore
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.data.sync.KeyVault
import com.sadturtleman.flowershoes.core.data.sync.Mailbox
import com.sadturtleman.flowershoes.core.data.sync.PairingSession
import com.sadturtleman.flowershoes.core.data.sync.PairingTransport
import com.sadturtleman.flowershoes.core.data.sync.SyncClient
import com.sadturtleman.flowershoes.core.data.sync.SyncScheduler
import com.sadturtleman.flowershoes.core.data.sync.RotationApprover
import com.sadturtleman.flowershoes.core.data.sync.PostRestoreRotation
import com.sadturtleman.flowershoes.core.data.sync.RotationRequester
import com.sadturtleman.flowershoes.core.data.sync.RotationTransport
import com.sadturtleman.flowershoes.core.data.sync.AccountTransport
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.data.sync.RelinkSession
import com.sadturtleman.flowershoes.core.data.sync.RelinkTransport
import com.sadturtleman.flowershoes.feature.onboarding.OnboardingViewModel
import kotlin.time.Clock
import kotlinx.coroutines.flow.flatMapLatest
import com.sadturtleman.flowershoes.core.drawing.DrawingRasterizer
import com.sadturtleman.flowershoes.core.media.DrawingPng
import okio.Path
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 서버 쪽 구현 묶음. Firebase 설정이 있는 빌드에서만 플랫폼이 만들어 넘긴다. */
class CloudBackend(val mailbox: Mailbox, val blobs: BlobStore, val pairing: PairingTransport, val rotation: RotationTransport? = null, val account: AccountTransport? = null, val relink: RelinkTransport? = null)

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
        single { UnlinkService(get(), get(), get(), cloud?.account, wipeAttachmentFiles = { get<AttachmentStore>().deleteAllFiles() }) }
        // 서버 설정이 없는 빌드에서는 등록하지 않는다(화면은 getOrNull 로 "설정 전" 안내를 보여 준다).
        if (cloud != null) {
            single { PairingSession(get(), get(), get(), cloud.pairing, get()) }
            single { SyncClient(get(), get(), get(), cloud.mailbox, cloud.blobs, get(), get(), get(), get()) }
            cloud.relink?.let { r -> single { RelinkSession(get(), get(), get(), r, get(), get()) } }
            single {
                val client = get<SyncClient>()
                val approver = cloud.rotation?.let { RotationApprover(get(), get(), get(), it, drainInbox = { client.syncOnce() }, get()) }
                val afterRestore = cloud.rotation?.let { t ->
                    val settings = get<com.sadturtleman.flowershoes.core.data.DeviceSettings>()
                    PostRestoreRotation(get(), get(), RotationRequester(get(), get(), get(), t, get()), t, onFinished = { token, epoch ->
                        settings.setKeyEpoch(epoch)
                        cloud.account?.signInWithCustomToken(token)
                    })
                }
                val identity = get<com.sadturtleman.flowershoes.core.data.Identity>()
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                val doorbell = get<AppDatabase>().coupleDao().observe().flatMapLatest { c ->
                    if (c == null) kotlinx.coroutines.flow.emptyFlow() else cloud.mailbox.rings(c.id, identity.deviceId)
                }
                SyncScheduler(get(), client, approver, afterRestore, doorbell)
            }
        }
    },
    module {
        viewModel { OnboardingViewModel(get(), get(), get()) }
        viewModel { HomeViewModel(get(), get(), get(), get()) }
        viewModel { CalendarViewModel(get(), get()) }
        viewModel { LetterViewModel(get(), get(), get(), get()) }
        viewModel { DrawingEditorViewModel(get(), DrawingRasterizer { DrawingPng.render(it) }, get()) }
        viewModel { MemoryViewModel(get(), get(), get()) }
        viewModel { PairingViewModel(getOrNull(), getOrNull(), get(), getOrNull()) }
        viewModel { SettingsViewModel(get(), get(), get()) }
        viewModel { BackupViewModel(getOrNull(), get()) }
    },
)
