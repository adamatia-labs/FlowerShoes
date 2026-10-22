package com.sadturtleman.flowershoes.home.data

import com.sadturtleman.flowershoes.common.di.IoDispatcher
import com.sadturtleman.flowershoes.home.domain.HomeContentRepository
import com.sadturtleman.flowershoes.home.entity.HomeContentVO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 홈 본문 구현.
 *
 * 서버 API 가 정해지기 전이라 앱 안의 값으로 답한다. API 가 생기면 Retrofit 인터페이스를
 * 이 모듈에 두고(HomeDataModule 에서 `retrofit.create`), 응답을 [HomeContentVO] 로 옮기는 일만 여기서 한다.
 * 그때도 계약은 그대로라 domain · presentation 은 손대지 않는다.
 */
@Singleton
internal class HomeContentRepositoryImpl @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HomeContentRepository {

    override suspend fun getHomeContent(): HomeContentVO = withContext(ioDispatcher) {
        HomeContentVO(greeting = "꽃신에 오신 것을 환영합니다")
    }
}
