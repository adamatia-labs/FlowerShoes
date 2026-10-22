package com.sadturtleman.flowershoes.home.domain

import com.sadturtleman.flowershoes.featureflag.domain.FeatureFlagProvider
import com.sadturtleman.flowershoes.featureflag.domain.FlagKey
import com.sadturtleman.flowershoes.home.entity.HomeContentVO
import javax.inject.Inject

/**
 * 홈 본문 조회.
 *
 * 저장소가 준 본문에 배너를 얹는다. 배너는 서버가 아니라 [FlagKey.HomeBanner] 에서 오므로
 * 저장소가 아니라 여기서 합친다 — :featureflag:domain 이 순수 코틀린이라 도메인에서 그대로 주입받는다.
 */
class GetHomeContentUseCase @Inject constructor(
    private val homeContentRepository: HomeContentRepository,
    private val featureFlags: FeatureFlagProvider,
) {
    suspend operator fun invoke(): HomeContentVO =
        homeContentRepository.getHomeContent()
            .copy(banner = featureFlags.get(FlagKey.HomeBanner))
}
