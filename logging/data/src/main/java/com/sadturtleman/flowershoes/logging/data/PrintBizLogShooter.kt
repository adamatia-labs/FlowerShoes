package com.sadturtleman.flowershoes.logging.data

import com.sadturtleman.flowershoes.logging.domain.BizLog
import com.sadturtleman.flowershoes.logging.domain.BizLogShooter
import javax.inject.Inject

/**
 * 이벤트를 표준 출력으로 흘리는 전송기.
 *
 * 보낼 수집 서버가 없어서 이것이 유일한 구현이다. 외부 서비스를 붙이지 않는 대신,
 * 무엇이 언제 어떤 값으로 나가는지는 Logcat 에서 그대로 읽힌다("BIZLOG" 로 거르면 된다).
 * 서버가 생기면 이 클래스 자리에 그 구현을 놓고 [BizLogDataModule] 의 바인딩 한 줄만 바꾼다.
 *
 * android.util.Log 대신 println 을 쓰는 이유는 [BizLog] 를 찍는 데 안드로이드가 필요 없기 때문이다.
 * 안드로이드에서는 표준 출력도 Logcat 으로 들어오므로 보는 데는 차이가 없다.
 */
internal class PrintBizLogShooter @Inject constructor() : BizLogShooter {

    override suspend fun shoot(log: BizLog) {
        val parameters = log.event.parameters.entries.joinToString(", ") { "${it.key}=${it.value}" }
        println("$TAG: ${log.viewName} ${log.event.name} user=${log.userUuid} at=${log.recordedAt} {$parameters}")
    }

    private companion object {
        const val TAG = "BIZLOG"
    }
}
