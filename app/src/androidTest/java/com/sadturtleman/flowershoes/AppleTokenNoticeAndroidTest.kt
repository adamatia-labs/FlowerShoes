package com.sadturtleman.flowershoes

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.auth.*
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.feature.onboarding.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Provider response is simulated; Room, DataStore, ViewModel and the notice UI are real Android. */
@RunWith(AndroidJUnit4::class)
class AppleTokenNoticeAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedRegistrationCanBeRetriedOrAcknowledgedAtLargeFont(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "apple-notice-${UUID.randomUUID()}").apply { mkdirs() }
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = io) { root.toOkioPath() / "settings.preferences_pb" })
        val db = Room.databaseBuilder<AppDatabase>(context, File(root, "notice.db").absolutePath).buildKkotsin()
        val identity = object : Identity {
            override val userId = "local"
            override val deviceId = "device"
            override val keyEpoch = 0
        }
        var retries = 0
        val auth = object : AuthGateway {
            override val currentUserId = "owner"
            override val providers = listOf(AuthProvider.APPLE)
            override suspend fun signIn(provider: AuthProvider): SignInResult {
                val ready = AppleTokenRegistration({ false }, { true }).register("fresh-code")
                return SignInResult.Success("owner", provider, appleTokenNeedsAttention = !ready)
            }
            override suspend fun repairAppleToken(): SignInResult { retries++; return SignInResult.Failed(APPLE_TOKEN_MANUAL_HELP) }
            override suspend fun signOut() = Unit
        }
        val vm = withContext(Dispatchers.Main) {
            OnboardingViewModel(auth, settings, ProfileRepository(db, SyncWriter(db, identity), identity))
        }
        val mounted = mutableStateOf(true)
        try {
            settings.ensureIdentity()
            compose.setContent {
                if (mounted.value) {
                    val state by vm.state.collectAsState()
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                        KkotsinTheme(AppTheme.GOMSIN) { OnboardingScreen(state, vm::onIntent) }
                    }
                }
            }
            compose.runOnIdle { vm.onIntent(OnboardingIntent.SignIn(AuthProvider.APPLE)) }
            compose.waitUntil(15_000) { vm.state.value.appleTokenNotice }
            assertNotEquals("owner", settings.state.first().userId)
            compose.onNodeWithText("다시 확인").performScrollTo().performClick()
            compose.waitUntil(15_000) { retries == 1 && !vm.state.value.signingIn }
            compose.onNodeWithText(APPLE_TOKEN_NOTICE + "\n\n" + APPLE_TOKEN_MANUAL_HELP).assertExists()
            compose.onNodeWithText("계속하기").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(15_000) { vm.state.value.step == OnboardingStep.TERMS }
            assertEquals("owner", settings.state.first().userId)
            assertFalse(vm.state.value.appleTokenNotice)
        } finally {
            compose.runOnIdle { mounted.value = false }; compose.waitForIdle()
            vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            io.coroutineContext[Job]!!.cancelAndJoin(); db.close(); root.deleteRecursively()
        }
    }
}
