package com.sadturtleman.flowershoes.core.jank

import kotlin.math.roundToInt

/**
 * 디버그 빌드용. 한 줄 요약을 표준 출력으로 흘린다.
 *
 * 플랫폼 로거 대신 println 을 쓰는 것은 통계를 찍는 데 플랫폼이 필요 없기 때문이다 —
 * 안드로이드에서는 표준 출력도 Logcat 으로, iOS 에서는 Xcode 콘솔로 들어온다("JANK" 로 거르면 된다).
 */
internal class PrintJankReport : JankReport {

    override fun report(snapshot: JankSnapshot) {
        // String.format 은 jvm 전용이다. 소수 둘째 자리까지만 본다.
        val ratio = (snapshot.jankRatio * 10_000f).roundToInt() / 100.0
        println(
            "$TAG: [${snapshot.reason}] page=${snapshot.pageName} " +
                "frames=${snapshot.totalFrames} jank=${snapshot.jankFrames} " +
                "frozen=${snapshot.frozenFrames} ratio=$ratio% " +
                "avg=${snapshot.averageFrameDurationMillis}ms max=${snapshot.maxFrameDurationMillis}ms " +
                "states=${snapshot.states}",
        )
    }

    private companion object {
        const val TAG = "JANK"
    }
}

/**
 * 릴리스 빌드용. 보낼 곳이 아직 없어 아무 일도 하지 않는다.
 *
 * 로그로도 내보내지 않는 것은 사용자 기기에서 프레임마다 도는 계측이기 때문이다 —
 * 값을 받을 곳이 생기기 전까지는 비용만 남는다.
 * Firebase Performance 같은 수집기를 붙이면 이 클래스만 채운다.
 */
internal class NoOpJankReport : JankReport {
    override fun report(snapshot: JankSnapshot) = Unit
}
