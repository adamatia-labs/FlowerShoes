package com.sadturtleman.flowershoes.core.featureflag

import com.sadturtleman.flowershoes.core.logging.BizEvent
import com.sadturtleman.flowershoes.core.logging.BizLogger

/**
 * 플래그 · 실험 배정을 비즈니스 로그로 흘린다.
 *
 * 사용자 식별자는 여기서 붙이지 않는다 — [BizLogger] 가 기록 한 건마다 그 실행의 UUID 를 달아 주므로,
 * 나중에 지표에서 "이 사용자가 어떤 배정을 받고 무엇을 했는가" 가 같은 키로 이어진다.
 */
internal class BizLogFlagStateRecorder(
    private val bizLogger: BizLogger,
) : FlagStateRecorder {

    override fun record(environment: AppEnvironment, assignments: Map<String, String>) {
        // 플래그 수만큼 파라미터가 늘어나므로 GA4 한도(25개) 안으로 나눠 남긴다.
        BizEvent.FlagsResolved.split(environment = environment.name.lowercase(), assignments = assignments)
            .forEach { bizLogger.record(viewName = APP_VIEW, event = it) }
    }

    private companion object {
        /**
         * 화면에서 일어난 일이 아니라 앱이 뜨면서 일어난 일이다.
         * 실제 경로와 섞이지 않도록 화면 이름으로 쓰지 않는 값을 쓴다.
         */
        const val APP_VIEW = "/app"
    }
}
