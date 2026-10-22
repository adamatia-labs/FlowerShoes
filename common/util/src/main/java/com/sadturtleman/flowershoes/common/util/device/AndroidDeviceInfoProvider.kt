package com.sadturtleman.flowershoes.common.util.device

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import com.sadturtleman.flowershoes.common.util.device.reader.DeviceStateReader
import com.sadturtleman.flowershoes.common.util.device.reader.DisplayInfoReader
import com.sadturtleman.flowershoes.common.util.device.reader.InstallationIdStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [DeviceInfoProvider] 의 안드로이드 구현.
 *
 * **세션 내내 같은 값은 전부 `by lazy` 로 [SessionOnce] 에 모여 있다.** 앱이 도는 동안 바뀌지
 * 않는데도 부를 때마다 다시 읽으면 그게 그대로 비용이다 — `PackageManager` 조회는 바인더를 타고
 * [StatFs] 는 디스크를 건드린다. 생성자에서 미리 다 읽지 않는 이유도 같다. 이 객체는 앱 시작에
 * 만들어지는데, 그때 통신사·저장공간·설치 시각을 한꺼번에 읽으면 첫 화면이 그만큼 늦는다.
 * 항목마다 lazy 를 따로 두었으므로 **실제로 부른 것만** 그 자리에서 한 번 읽힌다.
 *
 * 구성이 바뀌면 달라지는 값과 순간의 값은 캐시하지 않고 [DisplayInfoReader] ·
 * [DeviceStateReader] 로 그때그때 읽는다.
 *
 * lazy 블록은 처음 부른 스레드에서 돈다. 메인 스레드에서 처음 부르면 그 조회만큼 메인이 멈춘다 —
 * 바인더 한 번짜리라 눈에 띄지는 않지만, 앱 시작 경로에서 굳이 부를 값은 아니다.
 */
internal class AndroidDeviceInfoProvider constructor(
    private val context: Context,
    private val buildVariant: AppBuildVariant,
    private val displayReader: DisplayInfoReader,
    private val stateReader: DeviceStateReader,
    private val installationIdStore: InstallationIdStore,
) : DeviceInfoProvider {

    private val once = SessionOnce()

    // --- 기기 · OS. 세션 1회다. --------------------------------------------------------------

    override fun getManufacturer(): String = once.manufacturer

    override fun getBrand(): String = once.brand

    override fun getModel(): String = once.model

    override fun getDeviceName(): String = once.deviceName

    override fun getHardware(): String = once.hardware

    override fun getSdkInt(): Int = once.sdkInt

    override fun getOsVersion(): String = once.osVersion

    override fun getSecurityPatch(): String? = once.securityPatch

    override fun getSupportedAbis(): List<String> = once.supportedAbis

    override fun getBuildType(): String = once.buildType

    override fun getFingerPrint(): String = once.fingerPrint

    override fun isProbablyEmulator(): Boolean = once.probablyEmulator

    // --- 화면 · 설정. 부를 때마다 읽는다. -----------------------------------------------------

    override fun getWindowSize(): WindowSize = displayReader.windowSize()

    override fun getWindowSize(context: Context): WindowSize = displayReader.windowSize(context)

    override fun getDensityDpi(): Int = displayReader.densityDpi()

    override fun getDensity(): Float = displayReader.density()

    override fun getRefreshRate(): Float = displayReader.refreshRate()

    override fun getFrameBudgetMillis(): Float =
        DeviceInfoRules.frameBudgetMillis(displayReader.refreshRate())

    override fun isDarkMode(): Boolean = displayReader.isDarkMode()

    override fun getLocales(): List<String> = displayReader.locales()

    override fun getFontScale(): Float = displayReader.fontScale()

    override fun getOrientation(): Orientation = displayReader.orientation()

    // --- 앱. 세션 1회다. ---------------------------------------------------------------------

    override fun getVersionName(): String = once.versionName

    override fun getVersionCode(): Long = once.versionCode

    override fun getBuildVariant(): AppBuildVariant = buildVariant

    override fun getFirstInstallTime(): Long = once.firstInstallTime

    override fun getLastUpdateTime(): Long = once.lastUpdateTime

    override fun getInstallerPackageName(): String? = once.installerPackageName

    override fun isSideloaded(): Boolean = once.sideloaded

    // --- 리소스. 저사양 여부 · 코어 수 · 저장공간만 세션 1회다. --------------------------------

    override fun getMemoryInfo(): MemoryInfo = stateReader.memoryInfo()

    override fun isLowRamDevice(): Boolean = once.lowRamDevice

    override fun getCpuCoreCount(): Int = once.cpuCoreCount

    override fun getBatteryPercent(): Int = stateReader.batteryPercent()

    override fun isCharging(): Boolean = stateReader.isCharging()

    override fun isPowerSaveMode(): Boolean = stateReader.isPowerSaveMode()

    override fun getThermalStatus(): ThermalStatus = stateReader.thermalStatus()

    override fun getAvailableStorageBytes(): Long = once.availableStorageBytes

    override fun getTotalStorageBytes(): Long = once.totalStorageBytes

    // --- 네트워크. 통신사만 세션 1회다. -------------------------------------------------------

    override fun getNetworkType(): NetworkType = stateReader.networkType()

    override fun isMeteredNetwork(): Boolean = stateReader.isMeteredNetwork()

    override fun getDownstreamBandwidthKbps(): Int = stateReader.downstreamBandwidthKbps()

    override fun getUpstreamBandwidthKbps(): Int = stateReader.upstreamBandwidthKbps()

    override fun getCarrierName(): String? = once.carrierName

    // --- 식별자 ----------------------------------------------------------------------------

    /**
     * 설치 ID 만 [SessionOnce] 에 두지 못한다 — 값을 만들려면 로컬 저장소를 읽어야 하고,
     * 그것은 `suspend` 라 lazy 블록 안에서 부를 수 없다. 잠금으로 같은 일을 손으로 한다.
     *
     * 잠그는 이유는 첫 실행 때문이다. 앱이 뜨자마자 로깅과 계측이 함께 부르면 두 코루틴이
     * 각자 UUID 를 만들어 뒤에 쓴 쪽이 이긴다 — 같은 설치가 두 사람으로 세어진다.
     * (저장소 안에서도 [InstallationIdStore] 가 한 번 더 막지만, 여기서 막으면 저장소를
     * 두 번 건드리지도 않는다.) 앱 수명에 한 번 걸리는 잠금이다.
     */
    private val installationIdLock = Mutex()

    @Volatile
    private var cachedInstallationId: String? = null

    override suspend fun getInstallationId(): String {
        cachedInstallationId?.let { return it }
        return installationIdLock.withLock {
            // 기다리는 동안 앞선 호출이 이미 채워 놓았을 수 있다.
            cachedInstallationId
                ?: installationIdStore.installationId().also { cachedInstallationId = it }
        }
    }

    override fun getAndroidId(): String? = once.androidId

    /**
     * 세션 내내 같은 값들. 처음 꺼낸 순간에 한 번만 읽힌다.
     *
     * 바깥 클래스가 아니라 홀더에 두는 것은 이름 때문이다 — 코틀린 프로퍼티 `manufacturer` 는
     * JVM 에서 게터 `getManufacturer()` 가 되어 우리가 구현한 같은 이름의 함수와 부딪친다.
     * 여기 모아 두면 이름을 줄이지 않아도 되고, "세션 1회짜리는 전부 이 안" 이라는 경계가
     * 눈에 보이는 덤이 생긴다.
     */
    private inner class SessionOnce {

        // Build 의 상수는 읽는 비용이 없다. 그래도 함께 두는 것은 주기가 같기 때문이다 —
        // "무엇이 세션 1회인가" 를 이 블록 하나로 답할 수 있게 한다.
        val manufacturer: String by lazy { Build.MANUFACTURER }
        val brand: String by lazy { Build.BRAND }
        val model: String by lazy { Build.MODEL }
        val deviceName: String by lazy { Build.DEVICE }
        val hardware: String by lazy { Build.HARDWARE }
        val sdkInt: Int by lazy { Build.VERSION.SDK_INT }
        val osVersion: String by lazy { Build.VERSION.RELEASE.orEmpty() }
        val securityPatch: String? by lazy {
            Build.VERSION.SECURITY_PATCH.takeUnless { it.isNullOrBlank() }
        }
        val supportedAbis: List<String> by lazy { Build.SUPPORTED_ABIS.orEmpty().toList() }
        val buildType: String by lazy { Build.TYPE }
        val fingerPrint: String by lazy { Build.FINGERPRINT }
        val probablyEmulator: Boolean by lazy {
            DeviceInfoRules.isProbablyEmulator(fingerPrint, brand, model, hardware)
        }

        /**
         * 앱 정보 넷이 같은 조회에서 나온다. 조회를 한 번만 하고 값을 꺼내 쓴다.
         *
         * 자기 패키지 조회라 실패할 이유는 사실상 없지만, 앱이 갱신되는 중에는 던진다.
         * 그 한순간 때문에 계측이 앱을 죽이게 두지는 않는다 — 못 읽으면 기본값이 나간다.
         */
        private val packageInfo: PackageInfo? by lazy {
            runCatching { readPackageInfo() }.getOrNull()
        }

        val versionName: String by lazy { packageInfo?.versionName.orEmpty() }
        val versionCode: Long by lazy { packageInfo?.let(::versionCodeOf) ?: 0L }
        val firstInstallTime: Long by lazy { packageInfo?.firstInstallTime ?: 0L }
        val lastUpdateTime: Long by lazy { packageInfo?.lastUpdateTime ?: 0L }
        val installerPackageName: String? by lazy { readInstallerPackageName() }
        val sideloaded: Boolean by lazy { DeviceInfoRules.isSideloaded(installerPackageName) }

        val lowRamDevice: Boolean by lazy { stateReader.isLowRamDevice() }
        val cpuCoreCount: Int by lazy { stateReader.cpuCoreCount() }

        /**
         * 앱 저장 영역의 파일시스템. 외부 저장소가 아니라 우리가 실제로 쓰는 곳이어야
         * I/O 가 느려진 이유를 여기서 읽을 수 있다.
         *
         * 남은 공간은 앱이 도는 동안에도 조금씩 변하지만 세션 1회로 둔다 — 여기서 보려는 것은
         * "이 기기가 저장 공간이 거의 없는 상태인가" 라는 코호트이지 정확한 바이트 수가 아니다.
         */
        private val statFs: StatFs? by lazy {
            runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        }

        val availableStorageBytes: Long by lazy { statFs?.availableBytes ?: 0L }
        val totalStorageBytes: Long by lazy { statFs?.totalBytes ?: 0L }

        /**
         * 통신사 이름. 권한이 필요 없는 값만 읽는다 —
         * 가입자 식별에 닿는 값은 READ_PHONE_STATE 를 부르고, 이 모듈은 그 권한을 요구하지 않는다.
         */
        val carrierName: String? by lazy {
            context.getSystemService(TelephonyManager::class.java)
                ?.networkOperatorName
                ?.takeUnless { it.isBlank() }
        }

        val androidId: String? by lazy { readAndroidId() }
    }

    // --- 버전 분기와 권한 있는 조회 ------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun readPackageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(packageInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

    /**
     * 누가 이 앱을 깔았는가.
     *
     * API 30 부터는 설치 주체가 원본 · 설치자 · 개시자로 갈라져 [PackageManager.getInstallSourceInfo]
     * 로만 볼 수 있다. 우리가 필요한 것은 "스토어를 거쳤는가" 하나라 설치자만 본다.
     */
    @Suppress("DEPRECATION")
    private fun readInstallerPackageName(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()?.takeUnless { it.isBlank() }

    /**
     * lint 가 HardwareIds 로 경고하는 값이 맞다. 광고 · 추적이 아니라 우리가 만든 설치 ID 가
     * 튀었을 때 대조할 보조 값으로만 쓴다.
     */
    @SuppressLint("HardwareIds")
    private fun readAndroidId(): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()?.takeUnless { it.isBlank() }
}
