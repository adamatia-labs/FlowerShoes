package com.sadturtleman.flowershoes.common.util.device

/**
 * 읽어 온 값에서 끌어내는 판단들.
 *
 * [AndroidDeviceInfoProvider] 안에 두면 `Build` 상수를 직접 읽는 코드와 섞여 테스트할 수 없다.
 * 판단만 순수 함수로 떼어 두면 JVM 테스트로 못 박을 수 있다 — 이쪽이 조용히 틀리는 종류의
 * 코드라 그 편이 낫다. 에뮬레이터를 실기기로 세면 통계가 오염되고, 사이드로드를 정상 설치로
 * 세면 크래시 원인을 엉뚱한 데서 찾는다.
 */
internal object DeviceInfoRules {

    /** 주사율을 읽지 못했을 때 가정하는 값. 가장 흔하고, 가장 너그러운 쪽이다. */
    private const val DEFAULT_REFRESH_RATE = 60f

    private val EMULATOR_HARDWARE = setOf("goldfish", "ranchu", "vbox86", "gce_x86", "cutf_cvm")

    /** 스토어로 인정하는 설치 주체. 여기 없으면 사이드로드로 분류된다. */
    private val KNOWN_STORES = setOf(
        "com.android.vending",
        "com.google.android.feedback",
        "com.sec.android.app.samsungapps",
        "com.amazon.venezia",
        "com.huawei.appmarket",
        "one.store.android",
    )

    fun isProbablyEmulator(
        fingerprint: String,
        brand: String,
        model: String,
        hardware: String,
    ): Boolean = fingerprint.startsWith("generic") ||
        fingerprint.contains("vbox") ||
        fingerprint.contains("emulator") ||
        brand.startsWith("generic") ||
        model.contains("google_sdk") ||
        model.contains("Emulator") ||
        model.contains("Android SDK built for") ||
        hardware in EMULATOR_HARDWARE

    fun isSideloaded(installerPackageName: String?): Boolean = installerPackageName !in KNOWN_STORES

    fun frameBudgetMillis(refreshRateHz: Float): Float =
        1_000f / if (refreshRateHz > 0f) refreshRateHz else DEFAULT_REFRESH_RATE
}
