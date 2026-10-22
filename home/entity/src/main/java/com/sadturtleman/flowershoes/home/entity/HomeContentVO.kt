package com.sadturtleman.flowershoes.home.entity

import com.sadturtleman.flowershoes.common.entity.featureflag.HomeBannerVO

/**
 * 홈 본문 한 벌.
 *
 * @param greeting 상단 인사 문구. 저장소가 채운다.
 * @param banner 상단 배너. 서버가 아니라 피처 플래그(`home_banner`)에서 오므로
 *  저장소가 아니라 UseCase 가 채운다. 문구가 비면 화면이 배너를 걸지 않는다.
 */
data class HomeContentVO(
    val greeting: String,
    val banner: HomeBannerVO = HomeBannerVO(title = ""),
)
