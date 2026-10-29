package com.sadturtleman.flowershoes.common.entity.featureflag

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 리모트 컨피그가 JSON 객체로 내려주는 값은 여기에 둔다.
 *
 * 원시 타입만 다룰 수 있었다면 배너 문구와 링크를 플래그 두 개로 쪼개야 하고,
 * 그러면 둘 중 하나만 바뀐 상태가 잠깐 화면에 나온다. 한 덩어리로 받으면 그 틈이 없다.
 */
@Serializable
data class HomeBannerVO(
    val title: String,
    val linkUrl: String? = null,
) {
    /** 문구가 없으면 배너를 걸지 않는다. */
    val isVisible: Boolean get() = title.isNotBlank()
}

/**
 * 홈 첫 보기 AB 테스트의 변형.
 *
 * 문자열로 두지 않는 이유는 콘솔에 `Grid` 나 `그리드` 가 적히면 조용히 기준 변형으로 떨어지기 때문이다.
 * enum 이면 그 순간 해석이 실패해 경고가 남고, 쓰는 쪽은 when 이 빠짐없이 채워졌는지 컴파일러가 본다.
 */
@Serializable
enum class HomeLayoutVariant {
    @SerialName("list")
    LIST,

    @SerialName("grid")
    GRID,
}
