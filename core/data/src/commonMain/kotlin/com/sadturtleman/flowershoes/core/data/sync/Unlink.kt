package com.sadturtleman.flowershoes.core.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.database.AppDatabase

/** 연결 해제 · 탈퇴의 서버 쪽(callable unlinkCouple · deleteAccount). */
interface AccountTransport {
    suspend fun unlinkCouple(coupleId: String)

    suspend fun deleteAccount()

    /** 기기 교체를 마친 뒤 finalizeDevice 가 준 토큰으로 다시 로그인한다(새 기기 클레임). */
    suspend fun signInWithCustomToken(token: String) = Unit
}

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
    suspend fun unlink(after: AfterUnlink) {
        val couple = db.coupleDao().get() ?: return
        transport?.unlinkCouple(couple.id)
        cleanUp(after)
    }

    suspend fun onPartnerUnlinked(after: AfterUnlink) = cleanUp(after)

    /**
     * 회원 탈퇴(7-5) = 연결 해제 + 이 폰 데이터 모두 지우기 + 서버의 users/{uid} · 백업 · 계정 삭제(함수 deleteAccount,
     * 카카오 연결 끊기 · Apple 토큰 폐기 포함) + 동기화된 백업 키 · 기기 키 삭제.
     */
    suspend fun deleteAccount(deleteSyncedBackupKey: suspend () -> Unit = {}) {
        transport?.deleteAccount()
        cleanUp(AfterUnlink.WIPE)
        db.useWriterConnection { tx -> tx.immediateTransaction { PROFILE_TABLES.forEach { t -> usePrepared("DELETE FROM $t") { it.step() } } } }
        deleteSyncedBackupKey()
        vault.clearAll()
        settings.reset()
    }

    private suspend fun cleanUp(after: AfterUnlink) {
        vault.clearCoupleKeys()
        db.useWriterConnection { tx ->
            tx.immediateTransaction {
                // 동기화 상태는 어느 쪽이든 비운다 — 다시 연결하면 새 커플(새 id · 새 키)이다.
                SYNC_TABLES.forEach { t -> usePrepared("DELETE FROM $t") { it.step() } }
                if (after == AfterUnlink.WIPE) SHARED_TABLES.forEach { t -> usePrepared("DELETE FROM $t") { it.step() } }
            }
        }
        // 커플 행은 남긴다(보관본의 "누구와의 추억"). 상대 id 를 비워 혼자 모드로 돌아간다.
        db.coupleDao().get()?.let { db.coupleDao().upsert(it.copy(partnerUserId = "", keyEpoch = 0)) }
        if (after == AfterUnlink.WIPE) {
            db.coupleDao().clear()
            wipeAttachmentFiles()
        }
        settings.setKeyEpoch(0)
    }

    private companion object {
        val SYNC_TABLES = listOf("sync_op", "stream_counter", "recv_cursor", "recv_pending", "peer_cursor", "revoked_device", "device")
        val SHARED_TABLES = listOf("letter", "drawing", "receipt", "event", "leave_grant", "miss_you", "status", "memory", "attachment")

        /** 탈퇴 때만: 내 정보 · 복무 · 시계까지. 연결 해제는 내 D-day 를 남긴다. */
        val PROFILE_TABLES = listOf("service", "user", "meta", "couple")
    }
}
