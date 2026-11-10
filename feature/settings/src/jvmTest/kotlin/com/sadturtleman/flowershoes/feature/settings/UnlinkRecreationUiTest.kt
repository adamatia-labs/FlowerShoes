package com.sadturtleman.flowershoes.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.runComposeUiTest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.data.sync.AccountTransport
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.LetterEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UnlinkRecreationUiTest {
    @Test
    fun `삭제 요청 중 설정 화면이 다시 만들어져도 삭제 선택과 완료가 유지된다`() = verifyRouteRecreation(fail = false)

    @Test
    fun `삭제 요청 중 설정 화면이 다시 만들어진 뒤 실패해도 삭제 선택이 유지된다`() = verifyRouteRecreation(fail = true)

    @Test
    fun `설정 화면이 없는 동안 실패해도 다시 열면 오류와 삭제 선택이 보인다`() = verifyRouteRecreation(fail = true, failWhileHidden = true)

    private fun verifyRouteRecreation(fail: Boolean, failWhileHidden: Boolean = false) {
        Dispatchers.setMain(Dispatchers.Unconfined)
        startKoin { }
        val fs = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-unlink-recreation-${Uuid.random()}"
        fs.createDirectories(root)
        val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val release = CompletableDeferred<Unit>()
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = dataScope) { root / "settings.preferences_pb" })
            runBlocking {
                settings.ensureIdentity()
                settings.bindAccount("owner")
                db.coupleDao().upsert(CoupleEntity("couple", "owner", "partner", 1, 100))
                db.letterDao().upsert(LetterEntity("letter", "partner", "시험 편지", "[]", "rose", 100, null, 1))
            }
            val identity = object : Identity {
                override val userId = "owner"
                override val deviceId = "device"
                override val keyEpoch = 1
            }
            val started = CompletableDeferred<Unit>()
            var serverCalls = 0
            val server = object : AccountTransport {
                override suspend fun unlinkCouple(coupleId: String) {
                    serverCalls++
                    started.complete(Unit)
                    release.await()
                    if (fail && serverCalls == 1) error("서버 응답 확인 불가")
                }
                override suspend fun deleteAccount() = Unit
            }
            val writer = SyncWriter(db, identity)
            val vm = SettingsViewModel(settings, ProfileRepository(db, writer, identity), UnlinkService(db, InMemoryKeyVault(), settings, server), identity, EventRepository(db, writer, identity))
            val job = vm.unlink(AfterUnlink.WIPE)
            runBlocking { withTimeout(10_000) { started.await() } }
            var shown by mutableStateOf(true)
            var completed = 0
            runComposeUiTest {
                setContent {
                    KkotsinTheme(theme = AppTheme.GOMSIN) {
                        if (shown) SettingsRoute(NoPlatformActions, {}, { completed++ }, viewModel = vm)
                    }
                }
                onNodeWithText("모두 지우기").assertIsSelected().assertIsNotEnabled()
                onNodeWithText("추억으로 보관").assertIsNotSelected().assertIsNotEnabled()
                onNodeWithText("연결 해제 중").assertIsNotEnabled()
                runOnIdle { shown = false }
                waitForIdle()
                if (failWhileHidden) {
                    runOnIdle { release.complete(Unit) }
                    waitUntil(timeoutMillis = 10_000) { vm.unlinkOperation.value is UnlinkOperation.Failed }
                }
                runOnIdle { shown = true }
                if (!failWhileHidden) {
                    onNodeWithText("모두 지우기").assertIsSelected().assertIsNotEnabled()
                    onNodeWithText("추억으로 보관").assertIsNotSelected().assertIsNotEnabled()
                    runOnIdle { release.complete(Unit) }
                }
                if (fail) {
                    waitUntil(timeoutMillis = 10_000) { vm.unlinkOperation.value is UnlinkOperation.Failed }
                    onNodeWithText("연결 해제 결과를 확인하지 못했어요. 연결 상태를 확인해 주세요").assertExists()
                    onNodeWithText("모두 지우기").assertIsSelected().assertIsEnabled()
                    onNodeWithText("추억으로 보관").assertIsNotSelected().assertIsEnabled().performClick()
                    onNodeWithText("추억으로 보관").assertIsSelected()
                    onNodeWithText("모두 지우기").assertIsNotSelected()
                    onNode(hasSetTextAction()).performTextInput("연결 해제")
                    onNode(hasText("연결 해제") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
                    waitUntil(timeoutMillis = 10_000) { completed == 1 }
                    runOnIdle { assertEquals(2, serverCalls) }
                    runBlocking {
                        assertEquals("", db.coupleDao().get()?.partnerUserId)
                        assertEquals("시험 편지", db.letterDao().get("letter")?.title)
                    }
                } else {
                    waitUntil(timeoutMillis = 10_000) { completed == 1 }
                    runOnIdle { assertEquals(1, completed) }
                    runBlocking { assertEquals(null, db.letterDao().get("letter")) }
                }
            }
            runBlocking { withTimeout(10_000) { job.join() } }
        } finally {
            release.complete(Unit)
            db.close()
            runBlocking { dataScope.coroutineContext[Job]!!.cancelAndJoin() }
            if (fs.exists(root)) fs.deleteRecursively(root)
            stopKoin()
            Dispatchers.resetMain()
        }
    }
}
