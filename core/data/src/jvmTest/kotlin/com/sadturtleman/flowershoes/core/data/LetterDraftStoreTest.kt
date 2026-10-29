package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class LetterDraftStoreTest {

    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val store = RoomLetterDraftStore(db)

    private val settingsPath = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/kkotsin-drafts-${Uuid.random()}.preferences_pb").toPath()
    private val unlink = UnlinkService(db, InMemoryKeyVault(), DeviceSettings(PreferenceDataStoreFactory.createWithPath { settingsPath }), null)

    @AfterTest
    fun close() {
        db.close()
        FileSystem.SYSTEM.delete(settingsPath)
    }

    @Test
    fun `쓰던 편지는 여러 통 남고 마지막으로 고친 것이 맨 위다`() = runTest {
        store.save("a", "{\"text\":\"첫 휴가\"}", atMs = 100)
        store.save("b", "{\"text\":\"오늘은\"}", atMs = 200)
        store.save("a", "{\"text\":\"첫 휴가 때\"}", atMs = 300)

        assertEquals(
            listOf(StoredDraft("a", "{\"text\":\"첫 휴가 때\"}", 300), StoredDraft("b", "{\"text\":\"오늘은\"}", 200)),
            store.observe().first(),
        )
    }

    @Test
    fun `지운 편지는 목록에서 빠진다`() = runTest {
        store.save("a", "{}", atMs = 100)
        store.save("b", "{}", atMs = 200)
        store.delete("a")
        assertEquals(listOf("b"), store.observe().first().map { it.id })
    }

    @Test
    fun `연결이 해제되면 사본을 남겨도 쓰던 편지는 지운다`() = runTest {
        store.save("a", "{}", atMs = 100)
        unlink.onPartnerUnlinked(AfterUnlink.KEEP_AS_MEMORIES)
        assertEquals(emptyList(), store.observe().first())
    }

    @Test
    fun `탈퇴하면 쓰던 편지를 지운다`() = runTest {
        store.save("a", "{}", atMs = 100)
        unlink.deleteAccount()
        assertEquals(emptyList(), store.observe().first())
    }
}
