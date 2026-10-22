package com.sadturtleman.flowershoes.core.sync

/**
 * 스트림 하나에 대해 받는 쪽이 아는 것: 연속으로 다 받은 마지막 번호와, 그보다 앞서 도착한 번호들.
 *
 * DB 의 RecvCursor · RecvPending 두 테이블을 한 값으로 본 것이다. 바꿀 때마다 새 값을 돌려준다.
 */
data class ReceiveState(
    val contiguousSeq: Long = 0,
    val pending: Set<Long> = emptySet(),
) {

    /** 이미 반영한 번호인지. 같은 Op 이 재전송·중복 조회로 다시 와도 한 번만 반영한다. */
    fun isDuplicate(seq: Long): Boolean = seq <= contiguousSeq || seq in pending

    /**
     * [seq] 를 받았다. 커서 바로 다음이면 커서를 옮기고, 이어지는 pending 번호가 있으면 계속 옮긴다.
     * 더 뒤의 번호면 pending 에 넣는다 — 그 사이에 빈 번호가 있다는 뜻이다.
     */
    fun receive(seq: Long): ReceiveState {
        if (isDuplicate(seq)) return this
        if (seq != contiguousSeq + 1) return copy(pending = pending + seq)
        var cursor = seq
        val rest = pending.toMutableSet()
        while (rest.remove(cursor + 1)) cursor++
        return ReceiveState(cursor, rest)
    }

    /**
     * 스냅샷이 "이 스트림은 [coversThrough] 까지 반영한 것으로 처리"라고 알려 왔다.
     * 지워진 번호를 영원히 기다리지 않도록 커서를 한 번에 옮기고 그 아래 pending 을 버린다.
     */
    fun applySnapshot(coversThrough: Long): ReceiveState {
        if (coversThrough <= contiguousSeq) return this
        var cursor = coversThrough
        val rest = pending.filterTo(mutableSetOf()) { it > coversThrough }
        while (rest.remove(cursor + 1)) cursor++
        return ReceiveState(cursor, rest)
    }

    /** 커서와 pending 사이에서 아직 안 온 번호. control 의 missing 에 실린다. */
    fun missing(): List<Long> {
        val top = pending.maxOrNull() ?: return emptyList()
        return (contiguousSeq + 1 until top).filter { it !in pending }
    }
}
