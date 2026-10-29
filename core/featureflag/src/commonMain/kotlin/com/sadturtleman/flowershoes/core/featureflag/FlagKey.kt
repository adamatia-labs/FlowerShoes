package com.sadturtleman.flowershoes.core.featureflag

import com.sadturtleman.flowershoes.common.entity.featureflag.HomeBannerVO
import com.sadturtleman.flowershoes.common.entity.featureflag.HomeLayoutVariant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer

/**
 * 이 앱이 가진 피처 플래그와 AB 테스트 전부.
 *
 * 한 곳에 모아 두는 이유는 두 가지다 — 리모트 키는 문자열이라 부르는 자리에서 지으면 오타가 조용히
 * 기본값으로 떨어지고, 어떤 실험이 돌고 있는지 코드에서 확인할 방법이 없어진다.
 * sealed 라 여기 없는 플래그는 읽을 수 없고, 새 플래그는 이 파일에 한 덩어리를 더한다.
 *
 * 키 하나가 (이름, 타입, 기본값) 을 함께 들고 있어 부르는 쪽에는 캐스팅도 기본값 처리도 없다 —
 * [FeatureFlagProvider.get] 이 그대로 T 를 돌려준다.
 *
 * 타입을 [serializer] 로 들고 다니는 이유는 [Class] 로는 제네릭이 지워지기 때문이다.
 * `List<Long>` 을 `Class` 로 적으면 원소가 Long 인지 String 인지 알 수 없어 원소 타입을 따로 받아야 하는데,
 * KSerializer 는 그것까지 담고 있어 원시 타입 · 리스트 · JSON 객체를 한 방법으로 읽는다.
 *
 * [stable] 은 "한 실행 동안 바뀌지 않아도 되는가" 다.
 * true 면 [FeatureFlagProvider.init] 이 받아 둔 값을 그대로 쓰고, false 면 읽을 때마다 새로 가져온다.
 * 화면 구성을 가르는 값은 도중에 바뀌면 같은 사용자가 두 화면을 다르게 보므로 stable 이어야 하고,
 * 타임아웃 · 재시도처럼 운영 중에 바로 듣게 하고 싶은 값만 false 로 둔다.
 *
 * @param key 환경 꼬리를 뺀 리모트 키. 실제로 찾는 이름은 [AppEnvironment.remoteKey] 가 만든다.
 * @param defaultValue 어느 환경에도 값이 없거나 읽지 못했을 때 쓰는 값. 앱은 이 값만으로도 돌아야 한다.
 */
sealed class FlagKey<T>(
    val key: String,
    val serializer: KSerializer<T>,
    val defaultValue: T,
    val stable: Boolean = true,
) {

    /** 로그와 지표에서 이 플래그를 가리키는 이름. */
    override fun toString(): String = key

    // ---- 기능 스위치 ----

    /** 온보딩 개편을 켠다. */
    data object NewOnboarding : FlagKey<Boolean>(
        key = "new_onboarding",
        serializer = Boolean.serializer(),
        defaultValue = false,
    )

    /**
     * 홈 목록이 한 번에 받아 오는 행 수.
     *
     * 도중에 바뀌면 목록 길이가 화면 안에서 달라지므로 stable 이다.
     */
    data object HomePageSize : FlagKey<Int>(
        key = "home_page_size",
        serializer = Int.serializer(),
        defaultValue = 13,
    )

    /** 네트워크 재시도 횟수. 운영 중에 바로 듣게 하려고 stable 이 아니다. */
    data object NetworkRetryCount : FlagKey<Int>(
        key = "network_retry_count",
        serializer = Int.serializer(),
        defaultValue = 3,
        stable = false,
    )

    /** 네트워크 타임아웃(ms). 운영 중에 바로 듣게 하려고 stable 이 아니다. */
    data object FetchTimeoutMillis : FlagKey<Long>(
        key = "fetch_timeout_ms",
        serializer = Long.serializer(),
        defaultValue = 5_000L,
        stable = false,
    )

    /**
     * 재시도 사이에 기다리는 시간(ms). 몇 번째 재시도인지가 곧 인덱스다.
     *
     * 값이 리스트라 `Class` 로는 원소 타입이 지워진다 — 키가 [serializer] 를 들고 있는 이유가 이것이다.
     */
    data object RetryBackoffMillis : FlagKey<List<Long>>(
        key = "retry_backoff_ms",
        serializer = ListSerializer(Long.serializer()),
        defaultValue = listOf(1_000L, 2_000L, 4_000L),
        stable = false,
    )

    /**
     * 홈 상단 배너. JSON 객체 하나로 내려온다.
     *
     * 문구와 링크를 플래그 둘로 쪼개지 않는 것은 한쪽만 바뀐 상태가 화면에 나오지 않게 하려는 것이다.
     */
    data object HomeBanner : FlagKey<HomeBannerVO>(
        key = "home_banner",
        serializer = HomeBannerVO.serializer(),
        defaultValue = HomeBannerVO(title = ""),
    )

    // ---- AB 테스트 ----
    // 배정을 바꾸면 같은 사용자가 다른 화면을 보게 되므로 전부 stable 이다.

    /**
     * 홈을 처음 열었을 때의 보기 방식. `list` 와 `grid` 를 겨룬다.
     *
     * 지표는 이미 있다 — 사용자가 보기를 직접 바꾸면 `layout_toggle` 이 남는다.
     */
    data object HomeLayoutAb : FlagKey<HomeLayoutVariant>(
        key = "ab_home_default_layout",
        serializer = serializer(),
        defaultValue = HomeLayoutVariant.LIST,
    )

    companion object {

        /** 기능 스위치. */
        val featureFlags: List<FlagKey<*>> = listOf(
            NewOnboarding,
            HomePageSize,
            NetworkRetryCount,
            FetchTimeoutMillis,
            RetryBackoffMillis,
            HomeBanner,
        )

        /** AB 테스트. 값이 곧 변형이다. */
        val experiments: List<FlagKey<*>> = listOf(
            HomeLayoutAb,
        )

        val all: List<FlagKey<*>> = featureFlags + experiments

        /**
         * 시작할 때 한 번 받아 두는 것들.
         * 목록을 손으로 적지 않는 이유는 [stable] 과 어긋나는 순간 그 플래그가 조용히 기본값이 되기 때문이다.
         */
        val stableFlags: List<FlagKey<*>> = all.filter { it.stable }

        /** 읽을 때마다 새로 가져오는 것들. */
        val volatileFlags: List<FlagKey<*>> = all.filterNot { it.stable }
    }
}
