package com.sadturtleman.flowershoes.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.MetaEntity
import com.sadturtleman.flowershoes.core.database.StreamCounterEntity
import com.sadturtleman.flowershoes.core.database.SyncOpEntity
import com.sadturtleman.flowershoes.core.sync.OpKind
import com.sadturtleman.flowershoes.core.sync.StreamId
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** 이 기기가 누구인지. 로그인 · 기기 등록 뒤에 정해진다. */
interface Identity {
    val userId: String
    val deviceId: String

    /** 지금 쓰는 커플 키 번호. 연결 전(혼자 모드)이면 0. */
    val keyEpoch: Int
}

/**
 * 로컬 쓰기 + SyncOp 발행을 한 트랜잭션으로 묶는다.
 *
 * 둘이 따로 커밋되면 "화면에는 보이는데 상대에게는 안 간" 상태나 그 반대가 생긴다.
 * 혼자 모드에서도 Op 은 쌓인다 — 연결하면 스냅샷으로 한꺼번에 보낸다.
 */
class SyncWriter(
    private val db: AppDatabase,
    private val identity: Identity,
    private val clock: Clock = Clock.System,
) {
    private val stream get() = StreamId(identity.deviceId, StreamId.Channel.APP)

    /**
     * [localWrite] 에는 이번 쓰기의 lamport 가 넘어간다. 그 값을 행에 적고, 같은 값을 실은 Op 본문을 [payload] 로 만든다.
     * 돌려주는 값은 발행된 Op 의 opId 다.
     */
    suspend fun write(
        kind: OpKind,
        entity: String,
        entityId: String,
        localWrite: suspend (lamport: Long) -> Unit,
        payload: (lamport: Long) -> OpPayload,
    ): String = db.useWriterConnection { tx ->
        tx.immediateTransaction {
            val sync = db.syncDao()
            val lamport = (sync.meta(LAMPORT) ?: 0L) + 1
            sync.setMeta(MetaEntity(LAMPORT, lamport))
            localWrite(lamport)

            val seq = sync.nextSeq(stream.wire) ?: 1L
            sync.setNextSeq(StreamCounterEntity(stream.wire, seq + 1))
            val opId = Uuid.random().toString()
            sync.appendOp(
                SyncOpEntity(
                    stream = stream.wire, seq = seq, opId = opId, kind = kind.wire,
                    entity = entity, entityId = entityId, payload = OpCodec.encode(payload(lamport)),
                    lamport = lamport, keyEpoch = identity.keyEpoch, createdAtMs = clock.now().toEpochMilliseconds(),
                ),
            )
            opId
        }
    }

    companion object {
        internal const val LAMPORT = "lamport"
    }
}
