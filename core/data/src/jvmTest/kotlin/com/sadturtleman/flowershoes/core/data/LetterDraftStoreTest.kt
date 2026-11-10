package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.data.sync.AccountTransport
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
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class LetterDraftStoreTest {

    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val store = RoomLetterDraftStore(db)

    private val settingsPath = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/kkotsin-drafts-${Uuid.random()}.preferences_pb").toPath()
    private val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath { settingsPath })
    private val transport = object : AccountTransport {
        override suspend fun unlinkCouple(coupleId: String) = Unit
        override suspend fun deleteAccount() = Unit
    }
    private val unlink = UnlinkService(db, InMemoryKeyVault(), settings, transport)

    @Test
    fun `계정을 바꾸면 이전 계정의 초안이 보이지 않는다`() = runTest {
        settings.ensureIdentity()
        settings.bindAccount("account-a")
        store.save("private-a", "{\"text\":\"A의 편지\"}", 100)
        settings.bindAccount("account-b")
        val other = RoomLetterDraftStore(db)
        assertEquals(emptyList(), other.observe().first())
    }

    @Test
    fun `다른 계정은 같은 식별자로 원래 초안을 덮어쓸 수 없다`() = runTest {
        settings.ensureIdentity()
        settings.bindAccount("account-a")
        store.save("private-a", "original", 100)
        settings.bindAccount("account-b")
        val other = RoomLetterDraftStore(db)
        runCatching { other.save("private-a", "replacement", 200) }
        settings.bindAccount("account-a")
        assertEquals("original", store.observe().first().single().json)
    }

    @Test
    fun `다른 계정의 삭제 요청은 원래 초안을 보존한다`() = runTest {
        settings.ensureIdentity()
        settings.bindAccount("account-a")
        store.save("private-a", "original", 100)
        settings.bindAccount("account-b")
        val other = RoomLetterDraftStore(db)
        runCatching { other.delete("private-a") }
        settings.bindAccount("account-a")
        assertEquals(listOf("private-a"), store.observe().first().map { it.id })
    }

    @Test
    fun `관계 정리는 다른 계정과 소유자 미상 초안을 보존한다`() = runTest {
        db.letterDraftDao().upsert(com.sadturtleman.flowershoes.core.database.LetterDraftEntity("legacy", "unknown owner", 1))
        settings.ensureIdentity()
        settings.bindAccount("account-b")
        store.save("private-b", "B", 100)
        settings.bindAccount("account-a")
        store.save("private-a", "A", 200)
        unlink.onPartnerUnlinked(AfterUnlink.KEEP_AS_MEMORIES)
        assertEquals(setOf("legacy", "private-b"), db.letterDraftDao().observeAll().first().map { it.id }.toSet())
    }

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

    @Test
    fun `탈퇴 서버가 없으면 성공으로 처리하거나 쓰던 편지를 지우지 않는다`() = runTest {
        store.save("a", "{}", atMs = 100)
        val unavailable = UnlinkService(db, InMemoryKeyVault(), settings, null)
        assertFailsWith<IllegalStateException> { unavailable.deleteAccount() }
        assertEquals(listOf("a"), store.observe().first().map { it.id })
    }
}
