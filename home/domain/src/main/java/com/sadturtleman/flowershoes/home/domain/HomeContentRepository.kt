package com.sadturtleman.flowershoes.home.domain

import com.sadturtleman.flowershoes.home.entity.HomeContentVO

/**
 * 홈 본문 저장소 계약. 구현은 :home:data 에 있다.
 *
 * 계약은 화면이 필요한 만큼만 가진다. 다른 feature 와 같은 엔드포인트를 쓰게 되더라도
 * 각자 자기 계약을 두고, 여러 feature 가 정말 같은 것을 쓸 때만 :common:domain 으로 올린다.
 */
interface HomeContentRepository {

    /** 홈 본문. 실패는 예외로 올린다. */
    suspend fun getHomeContent(): HomeContentVO
}
