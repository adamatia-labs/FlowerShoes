package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.model.LetterBlock

/**
 * 편지 쓰기 · 글 사이의 그림 · 사진(Figma 최종 정리 › 편지 쓰기 "그림 · 사진 = 글자").
 *
 * 본문 글 안에 그림 · 사진 한 개마다 [OBJ] 한 글자를 둔다. k 번째 [OBJ] 는 [ComposeDraft.objects] 의 k 번째 —
 * "d:<그림 id>" 또는 "p:<사진 첨부 id>". 글자처럼 지우면 같이 빠지고, 글자 수(1000)에도 한 글자로 센다.
 */
internal const val OBJ = '￼'

internal fun drawingKey(id: String) = "d:$id"
internal fun photoKey(id: String) = "p:$id"

/** 커서 자리에 그림 · 사진 한 글자를 넣는다. 커서는 넣은 것 바로 뒤로. */
internal fun ComposeDraft.insertObject(key: String): ComposeDraft {
    val at = cursor.coerceIn(0, text.length)
    val order = text.take(at).count { it == OBJ }
    return copy(text = text.substring(0, at) + OBJ + text.substring(at), objects = objects.toMutableList().apply { add(order, key) }, cursor = at + 1, rev = rev + 1)
}

/**
 * 글 고치기. 지운 범위에 [OBJ] 가 있으면 그 그림 · 사진도 뺀다. 붙여 넣은 글의 [OBJ] 는 버린다(가리킬 것이 없다).
 * 앞뒤 같은 부분을 빼고 남은 가운데가 바뀐 범위다(커서 기준).
 */
internal fun ComposeDraft.editText(newRaw: String, newCursor: Int): ComposeDraft {
    val old = text
    var p = 0
    while (p < old.length && p < newRaw.length && old[p] == newRaw[p]) p++
    // 같은 글자가 이어져 있으면(자리 글자 둘) 어느 쪽을 지웠는지 모른다 — 바뀐 범위는 커서 앞에서 끝난다고 본다.
    p = p.coerceAtMost(newCursor.coerceAtLeast(0))
    var q = 0
    while (q < old.length - p && q < newRaw.length - p && old[old.length - 1 - q] == newRaw[newRaw.length - 1 - q]) q++
    val removed = old.substring(p, old.length - q)
    val inserted = newRaw.substring(p, newRaw.length - q)
    val strayObjects = inserted.count { it == OBJ }
    val cleanInserted = inserted.filter { it != OBJ }
    val firstRemoved = old.take(p).count { it == OBJ }
    val removedCount = removed.count { it == OBJ }
    val gone = objects.subList(firstRemoved.coerceAtMost(objects.size), (firstRemoved + removedCount).coerceAtMost(objects.size)).toSet()
    val merged = (old.substring(0, p) + cleanInserted + old.substring(old.length - q)).take(ComposeDraft.MAX_CHARS)
    val goneDrawings = gone.filter { it.startsWith("d:") }.map { it.removePrefix("d:") }.toSet()
    val gonePhotos = gone.filter { it.startsWith("p:") }.map { it.removePrefix("p:") }.toSet()
    return copy(
        text = merged,
        objects = objects.filterIndexed { i, _ -> i < firstRemoved || i >= firstRemoved + removedCount },
        drawings = drawings.filterNot { it.id in goneDrawings }.mapIndexed { i, d -> d.copy(orderInLetter = i) },
        photos = photos.filterNot { it in gonePhotos },
        cursor = (newCursor - strayObjects).coerceIn(0, merged.length),
    ).normalized()
}

/** k 번째 그림 · 사진(글 속 자리 기준)을 뺀다. */
internal fun ComposeDraft.removeObjectAt(k: Int): ComposeDraft {
    var seen = -1
    val at = text.indexOfFirst { it == OBJ && ++seen == k }
    if (at < 0) return this
    // 지운 자리를 커서로 넘겨 그 글자를 정확히 집고, 원래 커서는 그 뒤에 되돌린다.
    val keep = if (cursor > at) cursor - 1 else cursor
    return editText(text.removeRange(at, at + 1), at).let { it.copy(cursor = keep.coerceIn(0, it.text.length)) }
}

/**
 * 글 속 [OBJ] · [ComposeDraft.objects] · 그림 · 사진 목록을 서로 맞춘다.
 * 예전 임시저장(글 속 자리 없이 그림 · 사진만 있던 것)은 글 끝에 이어 붙인다.
 */
internal fun ComposeDraft.normalized(): ComposeDraft {
    val known = drawings.map { drawingKey(it.id) } + photos.map { photoKey(it) }
    var keys = objects.filter { it in known }.distinct()
    var body = text
    // 자리를 가리킬 것이 없는 OBJ 는 지운다(뒤에서부터 넘치는 만큼).
    var extra = body.count { it == OBJ } - keys.size
    while (extra > 0) {
        val i = body.lastIndexOf(OBJ)
        body = body.removeRange(i, i + 1)
        extra--
    }
    val missing = known.filter { it !in keys }
    while (body.count { it == OBJ } < keys.size + missing.size) body += OBJ
    keys = keys + missing
    return copy(text = body, objects = keys, cursor = cursor.coerceIn(0, body.length))
}

/** 보낼 블록: 글 조각과 그림 · 사진을 글 속 순서 그대로. */
internal fun ComposeDraft.toBlocks(): List<LetterBlock> {
    val d = normalized()
    val out = mutableListOf<LetterBlock>()
    val sb = StringBuilder()
    var k = 0
    fun flush() {
        val t = sb.toString().trim('\n', ' ')
        if (t.isNotBlank()) out += LetterBlock.Text(t)
        sb.clear()
    }
    d.text.forEach { ch ->
        if (ch == OBJ) {
            flush()
            val key = d.objects.getOrNull(k++) ?: return@forEach
            out += if (key.startsWith("d:")) LetterBlock.DrawingRef(key.removePrefix("d:")) else LetterBlock.Photo(key.removePrefix("p:"))
        } else {
            sb.append(ch)
        }
    }
    flush()
    return out
}

/** 글자 수 · 빈 편지 판단용: 그림 · 사진 자리를 뺀 글. */
internal val ComposeDraft.plainText: String get() = text.filter { it != OBJ }
