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

/**
 * GA4 자동 수집(first_open · session_start 등)을 끈다. iOS 디버그 빌드가 Firebase 설정 뒤에 부른다.
 *
 * Info.plist 의 FIREBASE_ANALYTICS_COLLECTION_ENABLED 로도 끄지만, 빌드 설정으로 채운 값은 글자("NO")로 들어가므로
 * 실행 중에도 한 번 더 끈다(이 값은 기기에 남는다). Android 는 디버그 매니페스트가 맡는다.
 */
fun disableFirebaseAnalyticsCollection() {
    Firebase.analytics.setAnalyticsCollectionEnabled(false)
}
