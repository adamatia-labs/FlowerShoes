package com.sadturtleman.flowershoes.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant

/** 상태 입력을 연 계정과 연결에만 쓰는 권한. 화면에서 내용을 바꾸거나 다시 만들지 않는다. */
class StatusEditSession internal constructor(internal val account: DeviceState, internal val couple: CoupleEntity)

class StatusEditChangedException : CancellationException("계정 또는 연결이 바뀌었어요. 홈에서 다시 열어 주세요")

class StatusEditorStore(
    private val db: AppDatabase,
    private val settings: DeviceSettings,
    private val identity: Identity,
    private val statuses: StatusRepository,
    private val clock: Clock = Clock.System,
) {
    fun observe(ownerUid: String) = statuses.observeLatest(ownerUid)

    suspend fun open(ownerUid: String): StatusEditSession? {
        val account = settings.state.first()
        val couple = db.coupleDao().get() ?: return null
        if (account.userId != ownerUid || couple.myUserId != ownerUid || couple.partnerUserId.isBlank()) return null
        return StatusEditSession(account, couple)
    }

    suspend fun save(session: StatusEditSession, label: String, color: Long, expiresAt: Instant?) {
        val value = label.trim()
        require(value.isNotEmpty() && value.length <= MAX_LABEL_LENGTH) { "상태는 1~40자로 적어 주세요" }
        commit(session) {
            require(expiresAt == null || expiresAt > clock.now()) { "끝나는 시각을 다시 골라 주세요" }
            statuses.set(value, color, expiresAt)
        }
    }

    suspend fun clear(session: StatusEditSession) = commit(session) { statuses.clear() }

    private suspend fun commit(session: StatusEditSession, write: suspend () -> Unit) {
        val applied = settings.withCurrentAccount(session.account) {
            db.useWriterConnection { tx -> tx.immediateTransaction {
                if (db.coupleDao().get() != session.couple || identity.userId != session.account.userId ||
                    identity.deviceId != session.account.deviceId || identity.keyEpoch != session.couple.keyEpoch) {
                    throw StatusEditChangedException()
                }
                db.requireNoCleanup()
                write()
            } }
        }
        if (!applied) throw StatusEditChangedException()
    }

    companion object { const val MAX_LABEL_LENGTH = 40 }
}
