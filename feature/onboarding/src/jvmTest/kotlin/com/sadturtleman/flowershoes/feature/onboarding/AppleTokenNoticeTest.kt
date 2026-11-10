package com.sadturtleman.flowershoes.feature.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.auth.*
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AppleTokenNoticeTest {
    @Test fun `저장 실패 안내는 계정 설정을 저장하기 전에 확인한다`(): Unit = runBlocking {
        fixture { vm, settings, auth ->
            vm.onIntent(OnboardingIntent.SignIn(AuthProvider.APPLE))
            await { vm.state.value.appleTokenNotice }
            assertEquals(OnboardingStep.LOGIN, vm.state.value.step)
            assertNotEquals("owner", settings.state.first().userId)
            vm.onIntent(OnboardingIntent.ContinueAppleSignIn)
            await { vm.state.value.step == OnboardingStep.TERMS }
            assertEquals("owner", settings.state.first().userId)
            assertFalse(vm.state.value.appleTokenNotice)
            assertEquals(0, auth.repairs)
        }
    }

    @Test fun `다른 폰에 연결된 계정도 안내를 확인한 뒤 기기 변경으로 이동한다`(): Unit = runBlocking {
        fixture(linkedElsewhere = true) { vm, settings, _ ->
            val event = async { vm.events.first() }
            vm.onIntent(OnboardingIntent.SignIn(AuthProvider.APPLE))
            await { vm.state.value.appleTokenNotice }
            assertFalse(event.isCompleted)
            vm.onIntent(OnboardingIntent.ContinueAppleSignIn)
            assertEquals(OnboardingEvent.DeviceChange, withTimeout(5_000) { event.await() })
            assertEquals("owner", settings.state.first().userId)
        }
    }

    @Test fun `재확인 성공은 원래 계정을 이어가고 계정이 바뀌면 이전 로그인을 적용하지 않는다`(): Unit = runBlocking {
        for (uid in listOf("owner", "other")) fixture { vm, settings, auth ->
            vm.onIntent(OnboardingIntent.SignIn(AuthProvider.APPLE))
            await { vm.state.value.appleTokenNotice }
            auth.result = SignInResult.Success(uid, AuthProvider.APPLE)
            vm.onIntent(OnboardingIntent.RetryAppleToken)
            await { !vm.state.value.signingIn && !vm.state.value.appleTokenNotice }
            if (uid == "owner") assertEquals("owner", settings.state.first().userId)
            else {
                assertNotEquals("owner", settings.state.first().userId)
                assertEquals(OnboardingStep.LOGIN, vm.state.value.step)
                assertNotNull(vm.state.value.error)
            }
            assertEquals(1, auth.repairs)
        }
    }

    @Test fun `재확인 취소와 실패는 안내를 유지하고 계속하기를 허용한다`(): Unit = runBlocking {
        for (result in listOf(SignInResult.Cancelled, SignInResult.Failed("offline"))) fixture { vm, _, auth ->
            vm.onIntent(OnboardingIntent.SignIn(AuthProvider.APPLE))
            await { vm.state.value.appleTokenNotice }
            auth.result = result
            vm.onIntent(OnboardingIntent.RetryAppleToken)
            await { !vm.state.value.signingIn && auth.repairs == 1 }
            assertTrue(vm.state.value.appleTokenNotice)
            vm.onIntent(OnboardingIntent.ContinueAppleSignIn)
            await { vm.state.value.step == OnboardingStep.TERMS }
        }
    }

    @Test fun `안내 중 SDK 계정이 바뀌면 계속하기가 예전 계정을 저장하지 않는다`(): Unit = runBlocking {
        fixture { vm, settings, auth ->
            vm.onIntent(OnboardingIntent.SignIn(AuthProvider.APPLE))
            await { vm.state.value.appleTokenNotice }
            auth.currentUserId = "other"
            vm.onIntent(OnboardingIntent.ContinueAppleSignIn)
            await { !vm.state.value.appleTokenNotice }
            assertNotEquals("owner", settings.state.first().userId)
            assertEquals(OnboardingStep.LOGIN, vm.state.value.step)
            assertNotNull(vm.state.value.error)
        }
    }

    @Test fun `설정 쓰기 대기 중 SDK 계정이 바뀌어도 이전 계정을 저장하지 않는다`(): Unit = runBlocking {
        val hold = Hold()
        try { fixture(hold = hold) { vm, settings, auth ->
            vm.onIntent(OnboardingIntent.SignIn(AuthProvider.APPLE))
            await { vm.state.value.appleTokenNotice }
            hold.active = true
            vm.onIntent(OnboardingIntent.ContinueAppleSignIn)
            withTimeout(5_000) { hold.entered.await() }
            auth.currentUserId = "other"
            hold.release.complete(Unit)
            await { !vm.state.value.signingIn }
            assertNotEquals("owner", settings.state.first().userId)
            assertEquals(OnboardingStep.LOGIN, vm.state.value.step)
            assertNotNull(vm.state.value.error)
        } } finally { hold.release.complete(Unit) }
    }

    private class Hold {
        var active = false
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private class Auth : AuthGateway {
        override var currentUserId: String? = "owner"
        override val providers = listOf(AuthProvider.APPLE)
        var repairs = 0
        var result: SignInResult = SignInResult.Cancelled
        override suspend fun signIn(provider: AuthProvider) = SignInResult.Success("owner", provider, appleTokenNeedsAttention = true)
        override suspend fun signOut() = Unit
        override suspend fun repairAppleToken(): SignInResult { repairs++; return result }
    }

    private suspend fun fixture(linkedElsewhere: Boolean = false, hold: Hold? = null, block: suspend (OnboardingViewModel, DeviceSettings, Auth) -> Unit) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "apple-notice-${Uuid.random()}"
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val delegate = PreferenceDataStoreFactory.createWithPath(scope = io) { root / "settings.preferences_pb" }
        val settings = DeviceSettings(object : DataStore<Preferences> {
            override val data = delegate.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (hold?.active == true) { hold.entered.complete(Unit); hold.release.await() }
                return delegate.updateData(transform)
            }
        })
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val identity = object : Identity {
            override val userId = "local"
            override val deviceId = "device"
            override val keyEpoch = 0
        }
        val auth = Auth()
        val vm = OnboardingViewModel(auth = auth, settings = settings,
            profile = ProfileRepository(db, SyncWriter(db, identity), identity), linkedElsewhere = { linkedElsewhere })
        try { settings.ensureIdentity(); block(vm, settings, auth) }
        finally {
            vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            io.coroutineContext[Job]!!.cancelAndJoin()
            db.close(); FileSystem.SYSTEM.deleteRecursively(root); Dispatchers.resetMain()
        }
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) delay(10) }
}
