package com.sadturtleman.flowershoes.core.logging

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics

/**
 * GA4(Firebase Analytics, GitLive). Firebase 가 설정된 뒤에만 부른다.
 *
 * iOS 는 앱 타깃이 SPM `FirebaseAnalyticsCore`(광고 ID 를 모으지 않는 변형)를 링크한다.
 */
fun firebaseAnalyticsSink(): AnalyticsSink {
    val analytics = Firebase.analytics
    return AnalyticsSink { name, parameters -> analytics.logEvent(name, parameters) }
}
