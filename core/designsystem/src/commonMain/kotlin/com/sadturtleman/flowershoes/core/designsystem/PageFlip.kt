package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** 화면 이동 시간. */
private const val SLIDE_MS = 300

private class FlipState<T>(var last: T) {
    var from: T? = null
    // 동작 줄이기로 애니메이션이 한 프레임 안에 끝나도 이전 장을 반드시 치운다.
    var hasFrom by mutableStateOf(false)
    var forward = true
    var generation = 0
    /** 장이 바뀐 첫 프레임: 애니메이션이 0 으로 돌아가기 전까지 넘김 시작 자세로 그린다. */
    var pending by mutableStateOf(false)
}

/**
 * 가로 화면 전환(위로 덮이는 화면 · 설정 하위 · 편지 읽기 · 추억 상세).
 *
 * 앞으로 가면 두 화면이 왼쪽으로, 뒤로 가면 오른쪽으로 이동한다.
 * [pageKey] 가 같으면 전환하지 않고 내용만 바꾼다.
 * 장마다 rememberSaveable 상태(스크롤 등)를 따로 지켜, 뒤로 와도 보던 자리 그대로다.
 */
@Composable
fun <T> KkPageFlip(
    target: T,
    pageKey: (T) -> Any,
    isForward: (from: T, to: T) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val s = remember { FlipState(target) }
    val progress = remember { Animatable(1f) }
    if (pageKey(target) != pageKey(s.last)) {
        s.from = s.last
        s.hasFrom = true
        s.forward = isForward(s.last, target)
        s.generation++
        s.pending = true
    }
    s.last = target
    LaunchedEffect(s.generation) {
        if (s.generation == 0) return@LaunchedEffect
        progress.snapTo(0f)
        s.pending = false
        progress.animateTo(1f, tween(SLIDE_MS, easing = FastOutSlowInEasing))
        s.hasFrom = false
        s.from = null
    }
    val animating by remember { derivedStateOf { progress.value < 1f } }
    val p = { if (s.pending) 0f else progress.value }

    @Composable
    fun Page(value: T, layer: Modifier) {
        val k = pageKey(value)
        key(k) {
            holder.SaveableStateProvider(k) {
                Box(Modifier.fillMaxSize().then(layer).background(KkTheme.colors.canvas)) { content(value) }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        @Suppress("UNCHECKED_CAST")
        val from = s.from as T
        // animating 은 늘 읽는다 — 넘김이 끝났을 때 이 자리가 다시 그려져 넘어간 장을 치운다.
        val flipping = animating || s.pending
        val pages = if (s.hasFrom && flipping) listOf(from, target) else listOf(target)
        pages.forEachIndexed { index, page ->
            val layer = if (pages.size == 1) Modifier else {
                val direction = if (s.forward) 1f else -1f
                if (index == 0) Modifier.slide { -direction * p() }
                else Modifier.slide { direction * (1f - p()) }
            }
            Page(page, layer)
        }
    }
}

private fun Modifier.slide(amount: () -> Float): Modifier = graphicsLayer {
    translationX = size.width * amount()
}
