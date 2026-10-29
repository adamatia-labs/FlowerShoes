package com.sadturtleman.flowershoes.core.logging

import com.sadturtleman.flowershoes.common.di.IoDispatcher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 기록기 조립.
 *
 * [analytics] 는 GA4 로 나가는 문이다. 플랫폼이 "릴리스이고 Firebase 설정이 있는가" 를 보고
 * [firebaseAnalyticsSink] 를 넘기거나 null 을 넘긴다 — null 이면 표준 출력으로 흘린다.
 * 디버그 기록을 GA4 에 보내지 않는 것은 개발 중의 클릭이 지표에 섞이면 되돌릴 수 없기 때문이다.
 */
fun bizLogModule(analytics: () -> AnalyticsSink?): Module = module {
    single<BizLogShooter> { analytics()?.let(::AnalyticsBizLogShooter) ?: PrintBizLogShooter() }
    single<BizLogClock> { SystemBizLogClock() }
    single<BizLogger> { createBizLogger(get(), get(), get(IoDispatcher)) }
}
