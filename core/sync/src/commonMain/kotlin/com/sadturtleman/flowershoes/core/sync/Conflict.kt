package com.sadturtleman.flowershoes.core.sync

import kotlin.time.Instant

/**
 * Lamport 시계. 로컬에서 쓸 때마다 하나 올리고, 받은 Op 의 값을 보면 그보다 크게 맞춘다.
 */
class LamportClock(start: Long = 0) {
    var value: Long = start
        private set

    fun tick(): Long = ++value

    fun observe(remote: Long) {
        if (remote > value) value = remote
    }
}

/**
 * 수정 가능한 공유 데이터 한 버전. [deleted] 면 tombstone 이다.
 */
data class Versioned(
    val lamport: Long,
    val writerDevice: String,
    val deleted: Boolean = false,
)

/**
 * LWW(마지막 쓰기 우선) 판정.
 *
 * lamport 가 큰 쪽이 이긴다. 같으면 삭제가 이긴다 — 수정과 삭제가 동시에 일어났다면 지운 사람의 뜻을 따른다.
 * 그래도 같으면 기기 id 로 정해 두 폰이 같은 답을 낸다.
 */
object Lww {
    fun incomingWins(local: Versioned?, incoming: Versioned): Boolean {
        if (local == null) return true
        if (incoming.lamport != local.lamport) return incoming.lamport > local.lamport
        if (incoming.deleted != local.deleted) return incoming.deleted
        return incoming.writerDevice > local.writerDevice
    }
}

/**
 * 폐기된 기기 하나. [cutoff] 는 스트림별로 인정하는 마지막 seq 다.
 *
 * cutoff 는 승인 직전에 우편함을 끝까지 받아 반영한 뒤 정하므로, 남아 있던 정상 Op 은 이미 반영돼 있다.
 */
data class RevokedDevice(
    val deviceId: String,
    val revokedAt: Instant,
    val cutoff: Map<StreamId, Long>,
)

/**
 * 받은 Op 을 받아들일지. 폐기된 기기의 cutoff 를 넘는 Op 은 버린다.
 *
 * 판정은 seq 로만 한다. createdAt 은 보낸 쪽이 적는 값이라 조작할 수 있고 시계도 어긋나므로,
 * revokedAt 과 비교해 버리는 일은 하지 않는다(로그에 남겨 살펴보는 용도로만 쓴다).
 */
class RevocationFilter(revoked: List<RevokedDevice>) {
    private val byDevice = revoked.associateBy { it.deviceId }

    fun accepts(op: SyncOp): Boolean = accepts(op.stream, op.seq)

    fun accepts(stream: StreamId, seq: Long): Boolean {
        val device = byDevice[stream.deviceId] ?: return true
        val limit = device.cutoff[stream] ?: return false
        return seq <= limit
    }
}
