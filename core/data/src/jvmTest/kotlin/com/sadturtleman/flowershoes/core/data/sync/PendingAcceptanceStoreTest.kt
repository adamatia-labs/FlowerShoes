package com.sadturtleman.flowershoes.core.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.InviteToken
import com.sadturtleman.flowershoes.core.crypto.Pairing
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.IdentityHolder
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

class PendingAcceptanceStoreTest {
    private class Phone {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-pending-${Uuid.random()}"
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val vault = InMemoryKeyVault()
        val identity = IdentityHolder()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var store = openStore()
        var settings = DeviceSettings(store)

        private fun openStore() = PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" }
        suspend fun start() {
            Sodium.init()
            settings.ensureIdentity()
            settings.bindAccount("receiver")
            identity.update(settings.state.first())
        }
        suspend fun reopen() {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = openStore()
            settings = DeviceSettings(store)
            identity.update(settings.state.first())
        }
        fun session(transport: PairingTransport) = PairingSession(db, identity, vault, transport, pending = settings.pendingInvites)
        suspend fun close() {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
        }
    }

    private suspend fun InMemoryCloud.invitation(): String {
        val token = InviteToken.random()
        createInvite(token, Invite("sender", "sender-device", Pairing.PublicKeys.of(DeviceKeys.generate()), "보낸 사람", (Clock.System.now() + 7.days).toEpochMilliseconds()))
        return token
    }

    @Test
    fun `실제 설정 파일을 다시 열어 수락을 복구하고 확정 뒤에만 지운다`() = runBlocking {
        val phone = Phone()
        try {
            phone.start()
            val cloud = InMemoryCloud().apply { holdAccepts = true }
            val token = cloud.invitation()
            assertIs<PairingState.Accepting>(phone.session(cloud).accept(token))
            phone.reopen()
            val restarted = phone.session(cloud)
            assertEquals("보낸 사람", assertIs<PairingState.Accepting>(restarted.poll()).inviterName)
            assertNotNull(phone.settings.pendingInvites.loadAcceptance("receiver", phone.identity.deviceId))
            cloud.releaseAccepts()
            assertIs<PairingState.Linked>(restarted.poll())
            phone.reopen()
            assertNull(phone.settings.pendingInvites.loadAcceptance("receiver", phone.identity.deviceId))
            assertEquals("sender", phone.db.coupleDao().get()?.partnerUserId)
        } finally { phone.close() }
    }

    @Test
    fun `로그인 캐시가 늦게 바뀌어도 다른 계정에서 이전 수락을 완료하거나 지우지 않는다`() = runBlocking<Unit> {
        val phone = Phone()
        try {
            phone.start()
            val cloud = InMemoryCloud().apply { holdAccepts = true }
            val token = cloud.invitation()
            phone.session(cloud).accept(token)
            val saved = assertNotNull(phone.settings.pendingInvites.loadAcceptance("receiver", phone.identity.deviceId))
            cloud.releaseAccepts()
            phone.settings.bindAccount("other") // IdentityHolder는 아직 receiver.
            assertIs<PairingState.Idle>(phone.session(cloud).poll())
            phone.settings.pendingInvites.clearAcceptance(saved)
            assertNull(phone.db.coupleDao().get())
            phone.settings.bindAccount("receiver")
            assertEquals(saved, phone.settings.pendingInvites.loadAcceptance("receiver", phone.identity.deviceId))
            assertIs<PairingState.Linked>(phone.session(cloud).poll())
        } finally { phone.close() }
    }

    @Test
    fun `연결을 적용하는 동안 계정 변경이 끼어들지 않는다`() = runBlocking {
        val phone = Phone()
        try {
            phone.start()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val applying = async {
                phone.settings.pendingInvites.withCurrentIdentity("receiver", phone.identity.deviceId) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            val switch = async { phone.settings.bindAccount("other") }
            kotlinx.coroutines.yield()
            assertFalse(switch.isCompleted)
            release.complete(Unit)
            assertTrue(applying.await())
            switch.await()
            var applied = false
            assertFalse(phone.settings.pendingInvites.withCurrentIdentity("receiver", phone.identity.deviceId) { applied = true })
            assertFalse(applied)
        } finally { phone.close() }
    }

    @Test
    fun `소유자를 모르는 구버전 보낸 초대는 임의로 현재 계정에 연결하지 않고 보존한다`() = runBlocking {
        val phone = Phone()
        try {
            phone.start()
            val cloud = InMemoryCloud()
            val token = cloud.invitation()
            val raw = "$token|${(Clock.System.now() + 7.days).toEpochMilliseconds()}"
            val key = stringPreferencesKey("pending_invite")
            phone.store.edit { it[key] = raw }
            assertIs<PairingState.Idle>(phone.session(cloud).poll())
            assertNull(phone.settings.pendingInvite.first())
            assertEquals(raw, phone.store.data.first()[key])
        } finally { phone.close() }
    }
}
