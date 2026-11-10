package com.sadturtleman.flowershoes.core.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.DraftSession
import com.sadturtleman.flowershoes.core.data.RelationshipArchive
import com.sadturtleman.flowershoes.core.database.AppDatabase
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first

/** 연결 해제 · 탈퇴의 서버 쪽(callable unlinkCouple · deleteAccount). */
interface AccountTransport {
    suspend fun unlinkCouple(coupleId: String)

    suspend fun deleteAccount()

    suspend fun deleteAccountWithOutcome(): AppleRevocation {
        deleteAccount()
        return AppleRevocation.UNKNOWN
    }

    /** 기기 교체를 마친 뒤 finalizeDevice 가 준 토큰으로 다시 로그인한다(새 기기 클레임). */
    suspend fun signInWithCustomToken(token: String) = Unit
}

enum class AppleRevocation { NOT_REQUIRED, REVOKED, MANUAL, UNKNOWN }

/** 해제할 때 내 폰 사본을 어떻게 할지(7-5). 상대 폰 사본은 상대가 고른다 — 내가 지울 수 없다. */
enum class AfterUnlink {
    /** 편지 · 그림 · 일정 · 추억까지 이 폰에서 모두 지운다. */
    WIPE,

    /** 읽기 전용으로 남긴다. 동기화는 끊긴다. 나중에 언제든 지울 수 있다. */
    KEEP_AS_MEMORIES,
}

/**
 * 연결 해제(설계 7-5). 둘 중 누구나 혼자 할 수 있고 되돌릴 수 없다.
 *
 * 1. 서버: 함수가 couples/{c} 전체 · Storage 첨부 · 남은 초대를 지우고 상대에게 한 번 알린다.
 * 2. 이 폰: 커플 키(모든 epoch)를 지우고, 동기화 상태(Op 로그 · 커서)를 비운다. 사본은 [AfterUnlink] 대로.
 * 상대 폰은 해제 알림(또는 다음 실행 시 couples 문서 없음)을 받으면 [onPartnerUnlinked] 로 같은 정리를 한다.
 */
class UnlinkService(
    private val db: AppDatabase,
    private val vault: KeyVault,
    private val settings: DeviceSettings,
    private val transport: AccountTransport?,
    /** "모두 지우기"일 때 첨부 파일(그림 문서 · 사진)도 디스크에서 지운다. */
    private val wipeAttachmentFiles: suspend () -> Unit = {},
) {
    suspend fun unlink(after: AfterUnlink): Unit = db.relationshipMutex.withLock {
        val couple = db.coupleDao().get() ?: return@withLock
        val draftSession = settings.draftSessions.first()
        val server = checkNotNull(transport) { "연결 해제 서버에 연결할 수 없어요" }
        server.unlinkCouple(couple.id)
        cleanUp(after, draftSession)
    }

    suspend fun onPartnerUnlinked(after: AfterUnlink, expectedCoupleId: String? = null): Unit = db.relationshipMutex.withLock {
        if (expectedCoupleId != null && db.coupleDao().get()?.id != expectedCoupleId) return@withLock
        cleanUp(after, settings.draftSessions.first())
    }

    /**
     * 회원 탈퇴(7-5) = 연결 해제 + 이 폰 데이터 모두 지우기 + 서버의 users/{uid} · 계정 삭제(함수 deleteAccount,
     * 카카오 연결 끊기 · Apple 토큰 폐기 포함) + 기기 키 삭제.
     */
    suspend fun deleteAccount(): Unit = db.relationshipMutex.withLock {
        val draftSession = settings.draftSessionForCleanup()
        // 서버 성공 뒤 로컬 정리가 중단돼도 삭제된 계정으로 서버를 다시 호출하지 않는다.
        val showAppleInstructions = settings.pendingAccountCleanup.first() ?: run {
            val result = checkNotNull(transport) { "탈퇴 서버에 연결할 수 없어요" }.deleteAccountWithOutcome()
            val manual = result == AppleRevocation.MANUAL ||
                (result in setOf(AppleRevocation.UNKNOWN, AppleRevocation.NOT_REQUIRED) && settings.accountProvider.first() == "APPLE")
            settings.setPendingAccountCleanup(manual)
            manual
        }
        cleanUp(AfterUnlink.WIPE, draftSession, accountDeletion = true)
        db.useWriterConnection { tx -> tx.immediateTransaction { PROFILE_TABLES.forEach { t -> usePrepared("DELETE FROM $t") { it.step() } } } }
        vault.platformVault.clearAll()
        settings.reset(showAppleInstructions)
    }

    /** 모든 진입점은 서버 요청부터 이 정리까지 relationshipMutex를 보유한다. */
    private suspend fun cleanUp(after: AfterUnlink, draftSession: DraftSession?, accountDeletion: Boolean = false) {
        var retainedDrafts = false
        val cleanDatabase: suspend () -> Unit = {
            db.useWriterConnection { tx ->
                tx.immediateTransaction {
                    val couple = db.coupleDao().get()
                    if (after == AfterUnlink.KEEP_AS_MEMORIES && couple != null) RelationshipArchive(db).archive(couple)
                    // 동기화 상태는 어느 쪽이든 비운다 — 다시 연결하면 새 커플(새 id · 새 키)이다.
                    SYNC_TABLES.forEach { t -> usePrepared("DELETE FROM $t") { it.step() } }
                    draftSession?.let { db.letterDraftDao().clearOwned(it.ownerUid) }
                    retainedDrafts = db.letterDraftDao().countSaved() > 0
                    // 올려 보낸 위치도 지운다 — 다시 연결하면 seq 가 1부터라, 남겨 두면 새 Op 을 "이미 보냄"으로 보고 보내지 않는다.
                    usePrepared("DELETE FROM meta WHERE `key` LIKE 'uploaded:%'") { it.step() }
                    if (after == AfterUnlink.WIPE) SHARED_TABLES.filterNot { it == "attachment" && retainedDrafts }.forEach { t -> usePrepared("DELETE FROM $t") { it.step() } }
                    // 보관 경계와 커플 상태를 함께 커밋한다. 중간 종료 후에도 이관을 반복하지 않는다.
                    if (after == AfterUnlink.WIPE) db.coupleDao().clear()
                    else couple?.let { db.coupleDao().upsert(it.copy(partnerUserId = "", keyEpoch = 0, sealedKeys = it.sealedKeys?.let { "" })) }
                }
            }
        }
        if (draftSession == null) cleanDatabase()
        else check(settings.withDraftSession(draftSession, invalidate = true, includeCleaning = accountDeletion, block = cleanDatabase)) { "로그인한 계정이 바뀌었어요" }
        // DB 정리가 실패하면 기존 관계가 롤백된다. 그 기록을 여는 키도 커밋 전까지 보존한다.
        vault.platformVault.clearCoupleKeys()
        // 커플 행은 남긴다(보관본의 "누구와의 추억"). 상대 id 를 비워 혼자 모드로 돌아간다.
        if (after == AfterUnlink.WIPE && !retainedDrafts) {
            wipeAttachmentFiles()
        }
        settings.setKeyEpoch(0)
    }

    private companion object {
        // 초안은 별도로 현재 계정만 지운다. 소유자 미상/다른 계정의 첨부도 보존한다.
        val SYNC_TABLES = listOf("sync_op", "stream_counter", "recv_cursor", "recv_pending", "peer_cursor", "revoked_device", "device")
        val SHARED_TABLES = listOf("letter", "drawing", "receipt", "event", "leave_grant", "miss_you", "status", "memory", "attachment", "book_cover")

        /** 탈퇴 때만: 내 정보 · 복무 · 시계까지. 연결 해제는 내 D-day 를 남긴다. */
        val PROFILE_TABLES = listOf("service", "user", "meta", "couple")
    }
}
