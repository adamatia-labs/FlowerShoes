package com.sadturtleman.flowershoes.core.sync

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * 보낸 Op 로그 한 스트림. 우편함 확인(ack)으로는 지우지 않고, 상대 control 의 커서가 넘어선 것만 지운다.
 *
 * [floor] 는 로그에 남아 있는 가장 오래된 seq 다. 이보다 앞의 번호를 달라는 요청은 다시 보낼 원본이 없으므로
 * 스냅샷으로 답해야 한다. 로그가 비어 있으면 다음에 쓸 번호가 floor 다.
 */
data class SendLog(
    val stream: StreamId,
    val ops: List<SyncOp>,
    val nextSeq: Long,
) {
    init {
        require(ops.zipWithNext().all { (a, b) -> b.seq == a.seq + 1 }) { "로그는 seq 가 이어져야 한다" }
    }

    val floor: Long get() = ops.firstOrNull()?.seq ?: nextSeq

    val lastSeq: Long get() = nextSeq - 1

    /** 상대가 [ackedSeq] 까지 반영했다고 확인했다. 그 이하는 지운다. */
    fun acknowledge(ackedSeq: Long): SendLog = copy(ops = ops.filter { it.seq > ackedSeq })

    /**
     * 상대가 오래 접속하지 않아 로그가 [maxSize] 를 넘었다. 오래된 것부터 지운다.
     * 지운 만큼 floor 가 올라가고, 상대가 돌아오면 스냅샷을 받게 된다.
     */
    fun cap(maxSize: Int = DEFAULT_MAX_SIZE): SendLog =
        if (ops.size <= maxSize) this else copy(ops = ops.takeLast(maxSize))

    companion object {
        const val DEFAULT_MAX_SIZE = 5_000
    }
}

/** 상대 control 을 읽고 보내는 쪽이 할 일. */
sealed interface ResendPlan {
    data object Nothing : ResendPlan

    /** 이 Op 들을 `resend: true` 로 우편함에 다시 올린다. 서버는 화면 알림 없이 data 메시지만 보낸다. */
    data class Resend(val ops: List<SyncOp>) : ResendPlan

    /** 이미 지운 Op 을 달라는 요청이다. 현재 DB 상태로 스냅샷을 만들어 [coversThrough] 와 함께 보낸다. */
    data class Snapshot(val coversThrough: Long) : ResendPlan
}

/**
 * 재전송 판단.
 *
 * - missing 에 적힌 번호는 바로 다시 보낸다.
 * - 커서보다 뒤인데 missing 에 없는 번호는 [staleAfter](기본 3일)가 지난 것만 보낸다.
 *   커서 갱신이 2분 디바운스 · 하루 1회라서, 이미 반영된 Op 을 다시 보내 알림이 또 울리는 일을 막는다.
 * - 요청이 floor 앞을 가리키면 다시 보낼 원본이 없으므로 스냅샷으로 답한다.
 *   백업에서 복원한 오래된 커서가 대표적이다.
 */
class ResendPlanner(private val staleAfter: Duration = 3.days) {

    fun plan(log: SendLog, peerCursor: Long, missing: List<Long>, now: Instant): ResendPlan {
        val wanted = missing.filter { it > peerCursor && it <= log.lastSeq }
        if (peerCursor + 1 < log.floor || wanted.any { it < log.floor }) {
            return ResendPlan.Snapshot(coversThrough = log.lastSeq)
        }
        val wantedSet = wanted.toSet()
        val ops = log.ops.filter { op ->
            op.seq > peerCursor && (op.seq in wantedSet || now - op.createdAt >= staleAfter)
        }
        return if (ops.isEmpty()) ResendPlan.Nothing else ResendPlan.Resend(ops)
    }
}
