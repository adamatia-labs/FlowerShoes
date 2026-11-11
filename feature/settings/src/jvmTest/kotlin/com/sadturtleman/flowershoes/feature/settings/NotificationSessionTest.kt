package com.sadturtleman.flowershoes.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSessionTest {
    @Test fun `이전 화면은 새 계정의 알림 미리보기 파일을 바꾸지 않는다`() = notificationTest(stale = true)
    @Test fun `현재 화면은 미리보기 파일과 알림 설정을 함께 바꾼다`() = notificationTest(stale = false)

    private fun notificationTest(stale: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "notification-session-${Uuid.random()}"
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        var vm: SettingsViewModel? = null
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = io) { root / "settings.preferences_pb" })
            settings.ensureIdentity(); settings.bindAccount("owner"); settings.setOnboarded()
            settings.setNotifyPrefs(NotificationPrefs(hidePreview = true))
            val marker = root / "hide_preview"
            FileSystem.SYSTEM.write(marker) { writeUtf8("1") }
            val platform = object : PlatformActions {
                override fun setHidePreview(hide: Boolean) {
                    if (hide) FileSystem.SYSTEM.write(marker) { writeUtf8("1") }
                    else FileSystem.SYSTEM.delete(marker, mustExist = false)
                }
            }
            val data = ScreenData(db, settings, settings.state.first(), null, AttachmentStore(db, root / "attachments"))
            val uploads = mutableListOf<String>()
            val editor = SettingsViewModel(data.settings, data.profile, UnlinkService(db, InMemoryKeyVault(), settings, null),
                data.identity, data.events, NotifyPrefsTransport { owner, _ -> uploads += owner }, screenSession = data.session)
            vm = editor
            if (stale) settings.bindAccount("other")
            val jobs = editor.viewModelScope.coroutineContext[Job]!!.children.toSet()
            editor.setNotify(NotificationPrefs(hidePreview = false), platform)
            val writes = editor.viewModelScope.coroutineContext[Job]!!.children.filter { it !in jobs }.toList()
            withContext(Dispatchers.Default) { withTimeout(5_000) { writes.joinAll() } }
            assertEquals(stale, FileSystem.SYSTEM.exists(marker))
            assertEquals(stale, settings.notifyPrefs.first().hidePreview)
            assertEquals(if (stale) emptyList() else listOf("owner"), uploads)
        } finally {
            vm?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            io.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            if (FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.deleteRecursively(root)
            Dispatchers.resetMain()
        }
    }
}
