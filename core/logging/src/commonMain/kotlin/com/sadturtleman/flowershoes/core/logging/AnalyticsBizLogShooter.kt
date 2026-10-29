package com.sadturtleman.flowershoes.core.logging

/**
 * GA4 로 이벤트 한 건을 내보내는 문. 릴리스 빌드에서는 [firebaseAnalyticsSink] 가 꽂힌다.
 *
 * Firebase 를 직접 부르지 않고 이 한 줄짜리 포트를 두는 것은, 이벤트를 GA4 모양으로 옮기는 규칙을
 * Firebase 없이(jvm 테스트에서) 확인하려는 것이다.
 */
fun interface AnalyticsSink {
    fun logEvent(name: String, parameters: Map<String, Any>)
}

/**
 * [BizLog] 를 GA4 이벤트로 옮겨 보낸다.
 *
 * 이벤트 이름은 [BizEvent.name] 그대로, 값은 [BizEvent.parameters] 에 화면 이름(`view_name`)을 더한다.
 * GA4 가 받지 않는 모양은 여기서 맞춘다 — 값이 없는 파라미터는 빼고(null 은 보낼 수 없다),
 * 숫자가 아닌 값은 글자로, 100자를 넘는 글자는 자른다(넘으면 GA4 가 그 값을 버린다).
 *
 * 사용자 UUID 와 기록 시각은 보내지 않는다. GA4 가 앱 인스턴스 ID 와 이벤트 시각을 스스로 붙이고,
 * 실행마다 바뀌는 UUID 를 파라미터로 실으면 값 종류만 늘어난다.
 */
class AnalyticsBizLogShooter(private val sink: AnalyticsSink) : BizLogShooter {

    override suspend fun shoot(log: BizLog) {
        val parameters = buildMap {
            log.event.parameters.forEach { (key, value) -> if (value != null) put(key, value.toAnalyticsValue()) }
            put(VIEW_NAME, log.viewName.toAnalyticsValue())
        }
        sink.logEvent(log.event.name, parameters)
    }

    private fun Any.toAnalyticsValue(): Any = when (this) {
        is Int -> toLong()
        is Long, is Double -> this
        is Float -> toDouble()
        else -> toString().take(MAX_VALUE_LENGTH)
    }

    private companion object {
        const val VIEW_NAME = "view_name"

        /** GA4 이벤트 파라미터 값의 최대 길이. */
        const val MAX_VALUE_LENGTH = 100
    }
}
