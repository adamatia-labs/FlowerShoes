package com.sadturtleman.flowershoes.tti.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import com.sadturtleman.flowershoes.tti.domain.Tti
import com.sadturtleman.flowershoes.tti.domain.TtiRecorder
import com.sadturtleman.flowershoes.tti.domain.TtiTimeline

/**
 * 화면 트리에 내려주는 기록기. Activity 가 주입받은 것을 그대로 꽂는다.
 * 꽂히지 않았으면 아래의 helper 들이 모두 아무 일도 하지 않는다(계측이 화면을 막지 않는다).
 */
val LocalTtiRecorder = staticCompositionLocalOf<TtiRecorder?> { null }

/** 지금 측정 중인 화면. [TtiPage] 안에서만 값이 있다. */
val LocalTtiPage = staticCompositionLocalOf<TtiPageScope?> { null }

/**
 * 화면 하나의 측정 손잡이.
 *
 * 같은 구간을 두 번 열거나 닫지 않도록 여기서 한 번 거른다 —
 * 컴포지션은 언제든 다시 실행되므로, 매번 저장소까지 내려보내면 그것대로 낭비다.
 */
class TtiPageScope internal constructor(
    private val recorder: TtiRecorder,
    val tti: Tti,
    val pageName: String,
) {
    private val started = mutableSetOf<TtiTimeline>()
    private val ended = mutableSetOf<TtiTimeline>()

    fun start(timeline: TtiTimeline) {
        if (!started.add(timeline)) return
        recorder.startRecord(timeline, tti, pageName)
    }

    fun end(timeline: TtiTimeline) {
        // 열린 적 없는 구간은 길이 0 으로 남긴다 — 로딩 없이 캐시로 바로 그려진 경우다.
        // 비워 두면 그 기록은 영영 완성되지 않아 네 구간이 통째로 사라진다.
        if (timeline !in started) {
            skip(timeline)
            return
        }
        if (!ended.add(timeline)) return
        recorder.endRecord(timeline, tti, pageName)
        // 마지막 구간이 닫혔다면 그 자리에서 쏜다. 큰 덩어리가 화면이 그려진 뒤에 끝나는 경우다.
        if (ended.size == TtiTimeline.REQUIRED_COUNT) shot()
    }

    /**
     * 이 화면에 [timeline] 에 해당하는 것이 아예 없다고 알린다 — 길이 0 인 구간으로 닫는다.
     *
     * 이미 열린 구간에는 손대지 않는다.
     */
    fun skip(timeline: TtiTimeline) {
        if (timeline in started) return
        start(timeline)
        end(timeline)
    }

    fun shot() {
        recorder.shot(tti, pageName)
    }
}

/**
 * 한 화면의 측정을 연다. 라우팅 테이블에서 화면을 감싸는 것이 기본 자리다 —
 * [TtiTimeline.VIEW_CREATE] 가 ViewModel 생성부터 첫 컴포지션까지를 덮어야 하기 때문이다.
 *
 * 같은 화면이 백스택에 두 번 쌓이면 컴포지션도 둘이라 측정도 둘로 갈린다.
 */
@Composable
fun TtiPage(pageName: String, content: @Composable () -> Unit) {
    val recorder = LocalTtiRecorder.current
    if (recorder == null) {
        content()
        return
    }
    val page = remember(pageName) { TtiPageScope(recorder, Tti(), pageName) }

    page.start(TtiTimeline.VIEW_CREATE)
    CompositionLocalProvider(LocalTtiPage provides page) {
        content()
    }
    page.end(TtiTimeline.VIEW_CREATE)
}

/**
 * [running] 이 true 인 동안 [timeline] 구간을 연다.
 *
 * 서버 요청은 `state is Loading`, 큰 덩어리는 이미지 painter 의 로딩 여부를 그대로 넘기면 된다.
 */
@Composable
fun TtiSpanEffect(timeline: TtiTimeline, running: Boolean) {
    val page = LocalTtiPage.current ?: return
    LaunchedEffect(page, timeline, running) {
        if (running) page.start(timeline) else page.end(timeline)
    }
}

/**
 * 이 화면에는 [timeline] 에 해당하는 것이 아예 없다고 알린다.
 *
 * 없는 구간을 비워 두면 그 기록은 영영 완성되지 않아 한 건도 나가지 않는다([TtiTimeline.REQUIRED_COUNT]).
 * 큰 사진이 없는 목록 화면의 [TtiTimeline.BIG_PART_LOADING] 이 여기에 해당한다 —
 * 0ms 로 남겨 두면 "잴 것이 없었다" 가 그대로 읽히고 합계도 달라지지 않는다.
 *
 * 목록의 썸네일마다 구간을 열지 않는 이유는 [TtiSpanEffect] 쪽에 적어 두었다.
 */
@Composable
fun TtiEmptySpanEffect(timeline: TtiTimeline) {
    val page = LocalTtiPage.current ?: return
    LaunchedEffect(page, timeline) {
        page.skip(timeline)
    }
}

/**
 * 데이터가 준비된 뒤 그 프레임이 실제로 그려지는 구간([TtiTimeline.VIEW_BINDING])을 재고,
 * 다 그려진 자리에서 [TtiRecorder.shot] 한다.
 *
 * 이미지 같은 큰 덩어리가 아직 내려오는 중이면 그 기록은 완성이 아니라 쏘이지 않고 남는다.
 * 남은 것은 마지막 구간이 닫힐 때, 또는 앱이 내려갈 때 나간다.
 *
 * @param ready 화면에 그릴 데이터가 상태에 들어왔는가.
 */
@Composable
fun TtiDrawnEffect(ready: Boolean) {
    val page = LocalTtiPage.current ?: return
    LaunchedEffect(page, ready) {
        if (!ready) return@LaunchedEffect
        page.start(TtiTimeline.VIEW_BINDING)
        // 이 컴포지션이 실려 나가는 프레임을 기다린다.
        withFrameNanos { }
        page.end(TtiTimeline.VIEW_BINDING)
        page.shot()
    }
}
