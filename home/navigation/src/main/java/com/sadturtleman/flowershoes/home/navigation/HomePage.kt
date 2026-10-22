package com.sadturtleman.flowershoes.home.navigation

import com.sadturtleman.flowershoes.common.navigation.NavRoute
import com.sadturtleman.flowershoes.common.navigation.Page

/**
 * 앱의 시작 페이지이자 탭 루트.
 *
 * 다른 feature 는 이 정의만 참조해 홈으로 이동한다(화면 구현은 :home:presentation 에 있다).
 */
object HomePage : Page {
    const val PATH = "/home"

    override fun toRoute(): NavRoute = NavRoute(PATH)
}
