package com.sadturtleman.flowershoes.deeplink

import android.net.Uri
import android.util.Log
import androidx.navigation3.runtime.NavKey
import com.sadturtleman.flowershoes.common.navigation.NavRoute
import com.sadturtleman.flowershoes.navigation.GenericNavKey
import com.sadturtleman.flowershoes.navigation.appRouteByPath
import com.sadturtleman.flowershoes.home.navigation.HomePage

private const val TAG = "[DeepLink]"

/**
 * deep-link [Uri] 를 [NavRoute] 로 변환한다.
 * - path: pathSegments 를 슬래시로 합쳐 in-app path 와 동일한 형식으로 정규화 (예: "/detail").
 * - args: 모든 query parameter 를 그대로 String 맵으로 옮긴다 (복합 타입은 호출부의 Args.from 이 디코딩).
 */
fun Uri.toNavRoute(): NavRoute {
    val segments = pathSegments?.takeIf { it.isNotEmpty() } ?: return NavRoute(HomePage.PATH)
    val path = "/" + segments.joinToString("/")
    val args = queryParameterNames
        .filter { it.isNotEmpty() }
        .associateWith { (getQueryParameter(it) ?: "") }
    return NavRoute(path, args)
}

/**
 * deep-link 진입 시 시작 백스택을 구성한다.
 * - URI 가 없거나 미등록 path 면 홈 단일 스택으로 fallback.
 * - 등록된 path 면 해당 [com.sadturtleman.flowershoes.navigation.AppRoute] 의
 *   syntheticStack 을 그대로 사용한다.
 */
fun resolveStartStack(uri: Uri?): List<NavKey> {
    if (uri == null) return listOf(GenericNavKey(HomePage.PATH))
    val route = uri.toNavRoute()
    val appRoute = appRouteByPath[route.path]
    if (appRoute == null) {
        Log.w(TAG, "No matching path for uri=$uri (path=${route.path})")
        return listOf(GenericNavKey(HomePage.PATH))
    }
    return appRoute.syntheticStack(route.args)
}

/**
 * 앱 실행 중 들어온 새 deep-link 를 처리할 [NavRoute] 로 변환.
 * 미등록 path 면 null 반환 (호출부가 무시 결정).
 */
fun resolveNewIntentRoute(uri: Uri): NavRoute? {
    val route = uri.toNavRoute()
    if (appRouteByPath[route.path] == null) {
        Log.w(TAG, "onNewIntent: unhandled uri=$uri (path=${route.path})")
        return null
    }
    return route
}
