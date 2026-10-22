package com.sadturtleman.flowershoes.core.sync

import kotlin.time.Instant

/**
 * 보내는 쪽 단위. 기기마다 앱 스트림 하나, iOS 는 위젯 전송용 스트림이 하나 더 있다.
 * 기기를 바꾸면 새 스트림이 된다.
 */
data class StreamId(val deviceId: String, val channel: Channel) {
    enum class Channel(val wire: String) { APP("app"), WIDGET("widget") }

    val wire: String get() = "$deviceId:${channel.wire}"

    override fun toString(): String = wire

    companion object {
        fun parse(wire: String): StreamId {
            val cut = wire.lastIndexOf(':')
            require(cut > 0) { "스트림 형식이 아니다: $wire" }
            val channel = Channel.entries.firstOrNull { it.wire == wire.substring(cut + 1) }
                ?: error("모르는 채널: $wire")
            return StreamId(wire.substring(0, cut), channel)
        }
    }
}

/**
 * 우편함 문서의 평문 kind. 서버가 알림 여부를 정하는 데 쓰므로 내용은 담지 않는다.
 *
 * [silent] 인 종류는 화면 알림 없이 data 메시지로만 깨운다.
 */
enum class OpKind(val wire: String, val silent: Boolean) {
    MISS_YOU("missyou", false),
    STATUS("status", false),
    LETTER("letter", false),
    LETTER_DRAWING("letter-drawing", false),
    SCHEDULED("scheduled", false),
    EVENT("event", false),
    RECEIPT("receipt", true),
    DELETE("delete", true),
    SNAPSHOT("snapshot", true);

    companion object {
        fun fromWire(wire: String): OpKind? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * 보낸 Op 로그의 한 줄이자 우편함에 올라가는 데이터 Op.
 *
 * [seq] 는 스트림마다 1씩 오르고 누락 검출 · 재동기화에 쓴다.
 * [lamport] 는 수정 가능한 데이터의 충돌 해결에만 쓴다 — 우편함은 순서를 보장하지 않으므로
 * lamport 로는 연속성을 알 수 없다.
 */
data class SyncOp(
    val stream: StreamId,
    val seq: Long,
    val opId: String,
    val kind: OpKind,
    val entity: String,
    val entityId: String,
    val payload: ByteArray,
    val lamport: Long,
    val keyEpoch: Int,
    val createdAt: Instant,
) {
    override fun equals(other: Any?): Boolean = other is SyncOp && other.opId == opId
    override fun hashCode(): Int = opId.hashCode()
}

/**
 * 받는 쪽이 올리는 커서 알림(제어 메시지). seq 가 없고 최신 값 하나로 덮어쓴다.
 *
 * cursor 에 seq 를 붙이면 그것도 확인을 받아야 하고, 그 확인이 다시 cursor 가 되어 끝나지 않는다.
 */
data class ControlCursor(
    val cursors: Map<StreamId, Long>,
    val missing: Map<StreamId, List<Long>>,
    val at: Instant,
)
