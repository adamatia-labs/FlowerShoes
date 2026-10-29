package com.sadturtleman.flowershoes.core.logging

import com.sadturtleman.flowershoes.core.logging.BizLog
import com.sadturtleman.flowershoes.core.logging.BizLogShooter

/**
 * 이벤트를 표준 출력으로 흘리는 전송기.
 *
 * 디버그 빌드와 Firebase 설정이 없는 빌드가 쓴다 — 개발 중의 기록이 GA4 지표에 섞이지 않고,
 * 무엇이 언제 어떤 값으로 나가는지는 Logcat · Xcode 콘솔에서 그대로 읽힌다("BIZLOG" 로 거르면 된다).
 *
 * 플랫폼 로거 대신 println 을 쓰는 이유는 [BizLog] 를 찍는 데 플랫폼이 필요 없기 때문이다.
 * 안드로이드에서는 표준 출력도 Logcat 으로, iOS 에서는 Xcode 콘솔로 들어오므로 보는 데는 차이가 없다.
 */
internal class PrintBizLogShooter : BizLogShooter {

    override suspend fun shoot(log: BizLog) {
        val parameters = log.event.parameters.entries.joinToString(", ") { "${it.key}=${it.value}" }
        println("$TAG: ${log.viewName} ${log.event.name} user=${log.userUuid} at=${log.recordedAt} {$parameters}")
    }

    private companion object {
        const val TAG = "BIZLOG"
    }
}
