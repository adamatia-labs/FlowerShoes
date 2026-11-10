package com.sadturtleman.flowershoes.core.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.RelationshipArchive
import com.sadturtleman.flowershoes.core.data.ScreenWriteSession
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CleanupEntity
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** 연결 해제 · 탈퇴의 서버 쪽(callable unlinkCouple · deleteAccount). */
interface AccountTransport {
    suspend fun unlinkCouple(coupleId: String)
    suspend fun deleteAccount()
    suspend fun deleteAccountWithOutcome(): AppleRevocation {
        deleteAccount()
        return AppleRevocation.UNKNOWN
    }
    /** 기기 교체 뒤 finalizeDevice가 준 토큰으로 로그인한다. */
    suspend fun signInWithCustomToken(token: String) = Unit
}

enum class AppleRevocation { NOT_REQUIRED, REVOKED, MANUAL, UNKNOWN }

/** 상대 폰의 사본은 상대가 고른다. */
enum class AfterUnlink { WIPE, KEEP_AS_MEMORIES }

/** 서버 요청 전 선택을 저장하고, 중단된 로컬 정리를 같은 선택으로 이어간다. */
class UnlinkService(
    private val db: AppDatabase,
    private val vault: KeyVault,
    private val settings: DeviceSettings,
    private val transport: AccountTransport?,
    private val wipeAttachmentFiles: suspend () -> Unit = {},
) {
    val pendingCleanup = combine(db.cleanupDao().observe(), settings.cleanupRequests) { journal, request -> journal ?: request }
    val pendingChoice = pendingCleanup.map { it?.choice?.let(AfterUnlink::valueOf) }

    /** 재시도 화면의 선택보다 먼저 저장한 원래 선택이 우선한다. */
    suspend fun unlink(after: AfterUnlink, screen: ScreenWriteSession? = null): AfterUnlink? = db.relationshipMutex.withLock {
        val cleanup = restoreJournal()?.also { screen?.requireCleanup(it) } ?: begin("UNLINK", after, screen) ?: return@withLock null
        resume(cleanup)
        AfterUnlink.valueOf(cleanup.choice)
    }

    suspend fun onPartnerUnlinked(after: AfterUnlink, expectedCoupleId: String? = null, screen: ScreenWriteSession? = null): Unit = db.relationshipMutex.withLock {
        val saved = restoreJournal()?.also { screen?.requireCleanup(it) }
        if (expectedCoupleId != null && (saved?.coupleId ?: db.coupleDao().get()?.id) != expectedCoupleId) return@withLock
        val cleanup = saved ?: begin("PARTNER", after, screen) ?: return@withLock
        resume(cleanup)
    }

    suspend fun deleteAccount(screen: ScreenWriteSession? = null): Unit = db.relationshipMutex.withLock {
        restoreJournal()?.let {
            screen?.requireCleanup(it)
            resume(it)
            if (it.source == "ACCOUNT") return@withLock
        }
        resume(checkNotNull(begin("ACCOUNT", AfterUnlink.WIPE, screen)))
    }

    /** 앱/백그라운드 재시작에서는 저장된 작업만 이어간다. 새 삭제는 시작하지 않는다. */
    suspend fun resumePending(): Boolean = db.relationshipMutex.withLock {
        val saved = restoreJournal()
        if (saved == null) {
            settings.releaseFinishedCleanupIf { db.cleanupDao().get() == null }
            false
        } else {
            resume(saved)
            true
        }
    }

    private suspend fun begin(source: String, after: AfterUnlink, screen: ScreenWriteSession? = null): CleanupEntity? {
        settings.releaseFinishedCleanupIf { db.cleanupDao().get() == null }
        val couple = db.coupleDao().get()
        if (source == "UNLINK" && couple == null) return null
        val legacyAccount = if (source == "ACCOUNT") settings.pendingAccountCleanup.first() else null
        if (source != "PARTNER" && legacyAccount == null) checkNotNull(transport) { "정리 서버에 연결할 수 없어요" }
        val owner = settings.state.first()
        val candidate = CleanupEntity(
            token = Uuid.random().toString(), ownerUid = owner.userId, deviceId = owner.deviceId,
            accountSession = owner.accountSession, source = source, choice = after.name,
            phase = if (source == "PARTNER" || legacyAccount != null) SERVER_DONE else REQUESTED,
            coupleId = couple?.id, coupleOwner = couple?.myUserId, partnerUid = couple?.partnerUserId,
            keyEpoch = couple?.keyEpoch, linkedAtMs = couple?.linkedAtMs, sealedKeys = couple?.sealedKeys,
            appleInstructions = legacyAccount ?: false,
        )
        screen?.requireCleanup(candidate)
        settings.reserveCleanup(candidate)
        return installJournal(candidate)
    }

    private suspend fun restoreJournal(): CleanupEntity? = db.cleanupDao().get() ?: settings.cleanupRequest()?.let {
        // 마지막 Room 삭제가 끝난 작업은 복원하지 않고 설정 잠금만 해제한다.
        if (settings.cleanupSettingsFinished(it.token)) it else installJournal(it)
    }

    private suspend fun installJournal(candidate: CleanupEntity): CleanupEntity {
        try {
            settings.withCleanupIdentity(candidate) {
                db.useWriterConnection { tx -> tx.immediateTransaction {
                    val saved = db.cleanupDao().get()
                    if (saved == null) {
                        if (db.coupleDao().get() != candidate.expectedCouple()) throw StaleCleanupTarget()
                        db.cleanupDao().save(candidate)
                    } else check(saved.token == candidate.token) { "이미 다른 정리가 진행 중이에요" }
                } }
            }
        } catch (stale: StaleCleanupTarget) {
            settings.cancelCleanupReservation(candidate.token) {
                db.useWriterConnection { tx -> tx.immediateTransaction {
                    db.cleanupDao().get() == null && db.coupleDao().get() != candidate.expectedCouple()
                } }
            }
            throw stale
        }
        return checkNotNull(db.cleanupDao().get())
    }

    private class StaleCleanupTarget : IllegalStateException("연결이 바뀌었어요. 현재 연결을 다시 확인해 주세요")

    private suspend fun resume(initial: CleanupEntity) {
        var cleanup = initial
        if (!settings.cleanupSettingsFinished(cleanup.token)) {
            settings.withCleanupIdentity(cleanup) { checkedWrite(cleanup) {} }
            if (cleanup.phase == REQUESTED) {
                val server = checkNotNull(transport) { "정리 서버에 연결할 수 없어요" }
                val manual = if (cleanup.source == "ACCOUNT") {
                    val appleAccount = settings.accountProvider.first() == "APPLE"
                    val result = server.deleteAccountWithOutcome()
                    result == AppleRevocation.MANUAL ||
                        (result in setOf(AppleRevocation.UNKNOWN, AppleRevocation.NOT_REQUIRED) && appleAccount)
                } else {
                    server.unlinkCouple(checkNotNull(cleanup.coupleId))
                    false
                }
                val next = cleanup.copy(phase = SERVER_DONE, appleInstructions = manual)
                checkedWrite(cleanup) { db.cleanupDao().save(next) }
                cleanup = next
            }
            if (cleanup.source == "ACCOUNT") settings.setPendingAccountCleanup(cleanup.appleInstructions)
            if (cleanup.phase == SERVER_DONE) cleanup = cleanDatabase(cleanup)
            if (cleanup.phase == DATABASE_DONE) {
                val next = cleanup.copy(phase = SIDE_EFFECTS_DONE)
                settings.withCleanupIdentity(cleanup) {
                    checkedWrite(cleanup) {
                        // DB 정리 커밋 전에는 키를 지우지 않는다. 파일/키 실패는 이 단계만 반복한다.
                        if (cleanup.source == "ACCOUNT") vault.platformVault.clearAll() else vault.platformVault.clearCoupleKeys()
                        if (cleanup.choice == AfterUnlink.WIPE.name && !cleanup.retainedDrafts) wipeAttachmentFiles()
                        db.cleanupDao().save(next)
                    }
                }
                cleanup = next
            }
            settings.finishCleanupSettings(cleanup) { checkedWrite(cleanup) {} }
        }
        settings.releaseCleanup(cleanup.token) {
            db.useWriterConnection { tx -> tx.immediateTransaction {
                val saved = db.cleanupDao().get()
                if (saved != null) {
                    check(saved == cleanup && db.coupleDao().get() == cleanup.expectedCouple()) { "진행 중인 연결 처리가 바뀌었어요" }
                    db.cleanupDao().delete(cleanup.token)
                }
            } }
        }
    }

    private suspend fun cleanDatabase(cleanup: CleanupEntity): CleanupEntity {
        var next = cleanup
        settings.withCleanupIdentity(cleanup) {
            checkedWrite(cleanup) {
                val couple = db.coupleDao().get()
                if (cleanup.choice == AfterUnlink.KEEP_AS_MEMORIES.name && couple != null) RelationshipArchive(db).archive(couple)
                db.useWriterConnection { tx -> tx.immediateTransaction {
                    SYNC_TABLES.forEach { table -> usePrepared("DELETE FROM $table") { it.step() } }
                    if (cleanup.ownerUid.isNotBlank()) db.letterDraftDao().clearOwned(cleanup.ownerUid)
                    val retained = db.letterDraftDao().countRetainedContent() > 0
                    usePrepared("DELETE FROM meta WHERE `key` LIKE 'uploaded:%'") { it.step() }
                    if (cleanup.choice == AfterUnlink.WIPE.name) {
                        SHARED_TABLES.filterNot { it == "attachment" && retained }.forEach { table -> usePrepared("DELETE FROM $table") { it.step() } }
                        db.coupleDao().clear()
                    } else couple?.let { db.coupleDao().upsert(it.copy(partnerUserId = "", keyEpoch = 0, sealedKeys = it.sealedKeys?.let { "" })) }
                    if (cleanup.source == "ACCOUNT") PROFILE_TABLES.forEach { table -> usePrepared("DELETE FROM $table") { it.step() } }
                    next = cleanup.copy(phase = DATABASE_DONE, retainedDrafts = retained)
                    db.cleanupDao().save(next)
                } }
            }
        }
        return next
    }

    /** 다른 DB 인스턴스의 변경도 확인과 실제 정리 사이에 들어오지 못하게 한다. */
    private suspend fun checkedWrite(cleanup: CleanupEntity, block: suspend () -> Unit) = db.useWriterConnection { tx ->
        tx.immediateTransaction {
            check(db.cleanupDao().get() == cleanup && db.coupleDao().get() == cleanup.expectedCouple()) { "진행 중인 연결 처리가 바뀌었어요" }
            block()
        }
    }

    private fun CleanupEntity.expectedCouple(): CoupleEntity? {
        val original = coupleId?.let { CoupleEntity(it, checkNotNull(coupleOwner), checkNotNull(partnerUid), checkNotNull(keyEpoch), checkNotNull(linkedAtMs), sealedKeys) }
        if (phase == REQUESTED || phase == SERVER_DONE) return original
        if (choice == AfterUnlink.WIPE.name || source == "ACCOUNT") return null
        return original?.copy(partnerUserId = "", keyEpoch = 0, sealedKeys = original.sealedKeys?.let { "" })
    }

    private companion object {
        const val REQUESTED = "REQUESTED"
        const val SERVER_DONE = "SERVER_DONE"
        const val DATABASE_DONE = "DATABASE_DONE"
        const val SIDE_EFFECTS_DONE = "SIDE_EFFECTS_DONE"
        val SYNC_TABLES = listOf("sync_op", "stream_counter", "recv_cursor", "recv_pending", "peer_cursor", "revoked_device", "device")
        val SHARED_TABLES = listOf("letter", "drawing", "receipt", "event", "leave_grant", "miss_you", "status", "memory", "attachment", "book_cover")
        val PROFILE_TABLES = listOf("service", "user", "meta", "couple")
    }
}
