package com.sadturtleman.flowershoes.core.jank

/**
 * 플랫폼이 넘겨준 프레임 한 장. JankStats · CADisplayLink 의 타입을 측정 규칙으로 들이지 않으려고 한 겹 옮겨 담는다.
 *
 * @param isJank 이 프레임이 마감을 놓쳤는가. Android 는 JankStats 가, iOS 는 [displayIntervalFrame] 이 주사율을 보고 판단한다.
 * @param durationMillis Android 는 UI 스레드가 이 프레임에 쓴 시간, iOS 는 직전 프레임과의 표시 간격(UI 작업 시간을 알려주는 공개 API 가 없다).
 * @param states 그 순간 화면이 무엇을 하고 있었는가(page · scrolling 등).
 */
data class JankFrame(
    val isJank: Boolean,
    val durationMillis: Long,
    val states: Map<String, String> = emptyMap(),
)

/** 한 번 내보내는 단위로 묶인 통계. */
data class JankSnapshot(
    val pageName: String,
    val reason: Reason,
    val totalFrames: Int,
    val jankFrames: Int,
    val frozenFrames: Int,
    val maxFrameDurationMillis: Long,
    val sumFrameDurationMillis: Long,
    val states: Map<String, String>,
) {
    /** 놓친 프레임의 비율. 프레임이 없으면 0 이다. */
    val jankRatio: Float
        get() = if (totalFrames == 0) 0f else jankFrames.toFloat() / totalFrames

    /** 프레임 하나당 평균 시간. 절대 길이가 아니라 눈으로 볼 기준선이다. */
    val averageFrameDurationMillis: Long
        get() = if (totalFrames == 0) 0L else sumFrameDurationMillis / totalFrames

    /** 통계가 나가는 계기. 무엇을 보다가 끊겼는지를 이 값이 가른다. */
    enum class Reason {
        /** 화면을 떠났다. 그 화면에서 쌓인 것을 넘긴다. */
        PAGE_EXIT,

        /** 앱이 백그라운드로 내려갔다. 남은 것을 잃지 않으려고 비운다. */
        BACKGROUND,

        /** 스크롤이 끝났다. 스크롤 구간만 따로 본다 — 가장 끊김이 눈에 띄는 구간이다. */
        SCROLL_END,

        /** 누적 비율이 임계치를 넘었다. 그 자리에서 내보내고 버킷을 비운다. */
        THRESHOLD_EXCEEDED,

        /** 한 프레임이 얼어붙을 만큼 길었다. 누적과 별개로 한 건씩 나간다. */
        FROZEN_FRAME,
    }
}

/**
 * 통계를 내보내는 곳. 어디로 보낼지는 [jankModule] 이 정한다.
 *
 * 메인 스레드에서 불린다 — JankStats · CADisplayLink 의 콜백이 그 위에서 돌기 때문이다.
 * 그래서 구현이 무거우면 그 자체가 프레임을 놓치게 만든다.
 */
fun interface JankReport {
    fun report(snapshot: JankSnapshot)
}
