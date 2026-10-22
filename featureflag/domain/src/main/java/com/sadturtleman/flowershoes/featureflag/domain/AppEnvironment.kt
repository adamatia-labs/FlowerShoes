package com.sadturtleman.flowershoes.featureflag.domain

/**
 * 지금 앱이 어느 환경으로 빌드됐는가. :app 의 빌드 플레이버가 정해 주입한다.
 *
 * 환경마다 플래그와 실험이 따로 있어야 하므로 리모트 키에 환경 이름을 붙여 나눈다 —
 * 프로젝트 하나 안에서 `new_onboarding_dev` · `new_onboarding_qa` · `new_onboarding_prod` 가 따로 산다.
 * 앞이 아니라 뒤에 붙이는 것은 한 플래그의 환경별 값이 같은 접두사로 모이게 하려는 것이다.
 *
 * 선언 순서가 곧 [fallbackChain] 이다. 아래쪽(더 열린 환경)으로만 흐른다.
 */
enum class AppEnvironment(private val keySuffix: String) {
    DEV("dev"),
    QA("qa"),
    PROD("prod"),
    ;

    /** [flag] 를 이 환경에서 찾을 때 쓰는 리모트 키. */
    fun remoteKey(flag: FlagKey<*>): String = "${flag.key}_$keySuffix"

    /**
     * 이 환경에서 값을 찾는 순서. 자기 자신부터 시작해 아래쪽 환경으로 내려간다.
     *
     * dev 는 qa · prod 값을 주워 쓰지만 prod 는 dev 를 보지 않는다.
     * 그래서 모든 키를 환경 수만큼 적어 두지 않아도 되고, dev 에서 켜 본 실험이 prod 로 새지도 않는다.
     */
    fun fallbackChain(): List<AppEnvironment> = entries.drop(ordinal)

    companion object {
        /**
         * 빌드가 심어 준 이름을 환경으로 읽는다. 모르는 이름이면 [PROD] 다 —
         * 환경을 잘못 읽었을 때 실험이 새는 쪽보다 아무것도 안 켜지는 쪽이 낫다.
         */
        fun of(name: String): AppEnvironment =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PROD
    }
}
