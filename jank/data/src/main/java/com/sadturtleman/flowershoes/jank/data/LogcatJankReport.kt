package com.sadturtleman.flowershoes.jank.data

import android.util.Log
import com.sadturtleman.flowershoes.jank.domain.JankReport
import com.sadturtleman.flowershoes.jank.domain.JankSnapshot
import javax.inject.Inject

/**
 * 디버그 빌드용. 한 줄 요약을 Logcat 으로 흘린다.
 *
 * 다른 계측 모듈과 달리 println 이 아니라 android.util.Log 를 쓰는 것은 이 모듈이 안드로이드
 * 전용이기 때문이다(:jank:domain 만 순수 코틀린이다). 태그로 걸러 보는 편이 프레임 통계처럼
 * 자주 나오는 로그에는 낫다.
 */
internal class LogcatJankReport @Inject constructor() : JankReport {

    override fun report(snapshot: JankSnapshot) {
        val ratio = "%.2f".format(snapshot.jankRatio * 100f)
        Log.d(
            TAG,
            "[${snapshot.reason}] page=${snapshot.pageName} " +
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
internal class NoOpJankReport @Inject constructor() : JankReport {
    override fun report(snapshot: JankSnapshot) = Unit
}
