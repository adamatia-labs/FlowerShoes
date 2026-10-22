package com.sadturtleman.flowershoes.featureflag.data

import com.sadturtleman.flowershoes.featureflag.domain.AppEnvironment
import com.sadturtleman.flowershoes.featureflag.domain.FlagStateRecorder
import com.sadturtleman.flowershoes.logging.domain.BizEvent
import com.sadturtleman.flowershoes.logging.domain.BizLogger
import javax.inject.Inject

/**
 * 플래그 · 실험 배정을 비즈니스 로그로 흘린다.
 *
 * 사용자 식별자는 여기서 붙이지 않는다 — [BizLogger] 가 기록 한 건마다 그 실행의 UUID 를 달아 주므로,
 * 나중에 지표에서 "이 사용자가 어떤 배정을 받고 무엇을 했는가" 가 같은 키로 이어진다.
 */
internal class BizLogFlagStateRecorder @Inject constructor(
    private val bizLogger: BizLogger,
) : FlagStateRecorder {

    override fun record(environment: AppEnvironment, assignments: Map<String, String>) {
        bizLogger.record(
            viewName = APP_VIEW,
            event = BizEvent.FlagsResolved(
                environment = environment.name.lowercase(),
                assignments = assignments,
            ),
        )
    }

    private companion object {
        /**
         * 화면에서 일어난 일이 아니라 앱이 뜨면서 일어난 일이다.
         * 실제 경로와 섞이지 않도록 화면 이름으로 쓰지 않는 값을 쓴다.
         */
        const val APP_VIEW = "/app"
    }
}
