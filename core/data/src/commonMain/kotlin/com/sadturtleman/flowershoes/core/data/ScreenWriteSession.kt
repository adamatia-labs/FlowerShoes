package com.sadturtleman.flowershoes.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.CleanupEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

class ScreenSessionChangedException : CancellationException("계정 또는 연결이 바뀌었어요. 화면을 다시 열어 주세요")

/** DataStore 잠금 안에서 확인한 실제 계정. 중첩된 초안/상태 쓰기는 같은 잠금을 다시 잡지 않는다. */
internal class AccountWriteBoundary(val account: DraftSession) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AccountWriteBoundary>
}

/** 편집 화면의 쓰기 권한. 실제 저장은 항상 DataStore → Room 순서로 검사한다. */
class ScreenWriteSession internal constructor(
    private val db: AppDatabase,
    private val settings: DeviceSettings,
    internal val account: DeviceState,
    private val couple: CoupleEntity?,
) {
    internal val owner = DraftSession(account.userId, account.deviceId, account.accountSession)

    /** 삭제 예약도 화면에서 확인한 계정/관계를 대상으로만 만든다. 예약 이후의 경합은 기존 journal이 검사한다. */
    internal fun requireCleanup(target: CleanupEntity) {
        requireOwner()
        if (target.ownerUid != account.userId || target.deviceId != account.deviceId || target.accountSession != account.accountSession ||
            target.coupleId != couple?.id || target.coupleOwner != couple?.myUserId || target.partnerUid != couple?.partnerUserId ||
            target.keyEpoch != couple?.keyEpoch || target.linkedAtMs != couple?.linkedAtMs) throw ScreenSessionChangedException()
    }

    internal suspend fun <T> write(block: suspend () -> T): T {
        val locked = currentCoroutineContext()[AccountWriteBoundary]
        if (locked != null) {
            if (locked.account != owner) throw ScreenSessionChangedException()
            return transaction(block)
        }
        var result: Result<T>? = null
        val applied = settings.withCurrentAccount(account) { result = Result.success(transaction(block)) }
        if (!applied) throw ScreenSessionChangedException()
        return checkNotNull(result).getOrThrow()
    }

    internal suspend fun <T> transaction(block: suspend () -> T): T = db.useWriterConnection { tx ->
        tx.immediateTransaction {
            requireOwner()
            db.requireNoCleanup()
            val current = db.coupleDao().get()
            if (current?.id != couple?.id || current?.myUserId != couple?.myUserId ||
                current?.partnerUserId != couple?.partnerUserId || current?.keyEpoch != couple?.keyEpoch ||
                current?.linkedAtMs != couple?.linkedAtMs) throw ScreenSessionChangedException()
            block()
        }
    }

    private fun requireOwner() {
        if (couple != null && couple.myUserId != account.userId) throw ScreenSessionChangedException()
    }
}

/** 현재 화면만 사용하는 Repository 묶음. 백그라운드 동기화의 전역 Repository와 수명을 공유하지 않는다. */
class ScreenData(
    db: AppDatabase,
    settings: DeviceSettings,
    account: DeviceState,
    couple: CoupleEntity?,
    attachments: AttachmentStore,
    clock: Clock = Clock.System,
) {
    val session = ScreenWriteSession(db, settings, account, couple)
    val identity: Identity = object : Identity {
        override val userId = account.userId
        override val deviceId = account.deviceId
        override val keyEpoch = couple?.keyEpoch ?: 0
    }
    private val writer = SyncWriter(db, identity, clock, session)
    val settings = settings.forScreen(session)
    val attachments = attachments.forScreen(session)
    val profile = ProfileRepository(db, writer, identity, clock = clock)
    val events = EventRepository(db, writer, identity, clock)
    val leave = LeaveRepository(db, writer, identity, clock)
    val letters = LetterRepository(db, writer, identity, clock)
    val missYou = MissYouRepository(db, writer, identity, clock)
    val statuses = StatusRepository(db, writer, identity, clock)
    val statusEditor = StatusEditorStore(db, this.settings, identity, statuses, clock)
    val covers = BookCoverRepository(db, writer, identity)
}
