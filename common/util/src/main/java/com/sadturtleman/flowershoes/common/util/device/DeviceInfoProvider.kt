package com.sadturtleman.flowershoes.common.util.device

import android.content.Context

/**
 * 기기 정보를 읽는 곳. 안드로이드의 여러 시스템 서비스를 한 계약 뒤로 숨긴다.
 *
 * 호출자가 `Build` · `PackageManager` · `ConnectivityManager` 를 직접 만지면 두 가지가 따라온다 —
 * 테스트에서 그 화면·기록기를 세울 수 없고, 버전 분기(API 28 의 longVersionCode,
 * API 29 의 발열, API 30 의 WindowMetrics)가 부르는 쪽마다 흩어진다.
 * 여기서 한 번만 분기하고 밖으로는 값 하나씩만 내보낸다.
 *
 * **항목 하나에 함수 하나다.** 덩어리로 묶어 내보내지 않는 이유는 부르는 쪽이 필요한 것만
 * 가져가게 하려는 것이다 — 크래시 리포트는 지문과 ABI 만, 프레임 통계는 주사율과 발열만 쓴다.
 * 묶어 두면 한 줄 남기려고 스무 개를 읽게 되고, 그 안에는 바인더를 타는 것이 섞여 있다.
 *
 * **읽는 비용은 항목마다 다르다.**
 * - 세션 내내 같은 값(기기 · OS · 앱 · 저사양 여부 · 저장공간 · 통신사 · 식별자)은 구현이
 *   `by lazy` 로 한 번만 읽어 둔다. 몇 번을 불러도 처음 한 번만 실제로 읽는다.
 * - 구성이 바뀌면 달라지는 값(창 · 밀도 · 주사율 · 다크모드 · 로케일 · 글꼴 배율 · 방향)과
 *   순간의 값(메모리 · 배터리 · 발열 · 네트워크)은 부를 때마다 다시 읽는다. 캐시하면 거짓이 된다.
 *
 * 뒤쪽 둘은 바인더 호출을 탄다. 프레임마다 부를 것이 아니라 남길 이벤트가 생겼을 때 부를 것이다.
 */
interface DeviceInfoProvider {

    // --- 기기 · OS. 전부 세션 1회다. ---------------------------------------------------------

    /** 제조사(`Build.MANUFACTURER`). 예) samsung */
    fun getManufacturer(): String

    /** 브랜드(`Build.BRAND`). 제조사와 다를 수 있다 — 같은 공장이 여러 브랜드로 낸다. */
    fun getBrand(): String

    /** 사용자에게 보이는 모델명(`Build.MODEL`). 예) SM-S911N */
    fun getModel(): String

    /** 내부 기기 이름(`Build.DEVICE`). 모델 하나가 지역별로 여러 device 를 갖는다. */
    fun getDeviceName(): String

    /** 보드·칩셋 이름(`Build.HARDWARE`). */
    fun getHardware(): String

    /** API 레벨(`Build.VERSION.SDK_INT`). 분기의 기준이라 OS 버전과 늘 함께 본다. */
    fun getSdkInt(): Int

    /** 사용자에게 보이는 OS 버전(`Build.VERSION.RELEASE`). 예) 15 */
    fun getOsVersion(): String

    /**
     * 보안 패치 수준(`Build.VERSION.SECURITY_PATCH`). 읽지 못하면 null.
     *
     * 특정 패치 이후에만 나는 OS 버그를 쫓을 때 쓴다. 평소 리포트에 실을 이유는 없다.
     */
    fun getSecurityPatch(): String?

    /** 지원 ABI(`Build.SUPPORTED_ABIS`)를 우선순위대로. 네이티브 크래시를 볼 때 필요하다. */
    fun getSupportedAbis(): List<String>

    /** 빌드 타입(`Build.TYPE`). user · userdebug · eng. */
    fun getBuildType(): String

    /** 빌드 지문(`Build.FINGERPRINT`). 같은 모델의 펌웨어 차이를 가른다. */
    fun getFingerPrint(): String

    /**
     * 에뮬레이터·개발 기기로 **보이는가**. 지문 · 모델 문자열로 내리는 추정이지 단정이 아니다.
     *
     * 통계에서 빼려고 보는 값이다 — 에뮬레이터 한 대가 프레임 통계 전체를 나쁘게 물들이고,
     * 사내 개발 기기의 값이 사용자 분포에 섞이면 코호트가 흐려진다.
     * 우회하려면 얼마든지 우회되므로 보안 판단에는 쓰지 말 것.
     */
    fun isProbablyEmulator(): Boolean

    // --- 화면 · 설정. 구성이 바뀌면 함께 바뀐다. 부를 때마다 읽는다. -----------------------------

    /**
     * 창의 픽셀 크기. 애플리케이션 컨텍스트 기준이다.
     *
     * 창 크기는 화면 크기가 아니다 — 멀티윈도우·팝업뷰에서는 우리 창만큼만 그린다.
     * 창을 정확히 재야 하면 Activity 를 넘기는 [getWindowSize] 를 쓴다.
     */
    fun getWindowSize(): WindowSize

    /** [context] 가 보는 창 기준. Activity 를 넘기는 것을 전제로 한다. */
    fun getWindowSize(context: Context): WindowSize

    /** `DisplayMetrics.densityDpi`. */
    fun getDensityDpi(): Int

    /** px = dp × density 의 그 배율. */
    fun getDensity(): Float

    /**
     * 주사율(Hz). 읽지 못하면 0.
     *
     * 세션 1회가 아닌 것은 가변 주사율 기기 때문이다 — 120 으로 시작해 배터리를 아끼려 60 으로
     * 내려간다. 처음 값을 들고 있으면 그 뒤의 프레임을 두 배 너그러운 기준으로 재게 된다.
     */
    fun getRefreshRate(): Float

    /**
     * 프레임 하나에 허락된 시간(ms). :jank 의 판정 기준선이다.
     *
     * 60Hz 의 16.7ms 를 상수로 박아 두면 120Hz 기기에서 이미 끊긴 프레임이 정상으로 적힌다.
     * 주사율을 읽지 못했으면 60Hz 로 본다(가장 흔하고, 가장 너그러운 쪽).
     */
    fun getFrameBudgetMillis(): Float

    /** 다크 모드로 그리고 있는가(`Configuration.uiMode`). */
    fun isDarkMode(): Boolean

    /** 사용자가 설정한 로케일 전부를 우선순위대로. BCP 47 태그다. 예) ko-KR */
    fun getLocales(): List<String>

    /** 사용자 글꼴 배율. 1.0 이 기본이고 커질수록 레이아웃이 밀린다. */
    fun getFontScale(): Float

    /** 세로 · 가로. */
    fun getOrientation(): Orientation

    // --- 앱. 세션 1회다. ---------------------------------------------------------------------

    /** 사용자에게 보이는 버전 이름. 플레이버 접미사가 붙은 그대로다. */
    fun getVersionName(): String

    /** `PackageInfo.longVersionCode`. API 28 아래에서는 `versionCode` 를 올려 담는다. */
    fun getVersionCode(): Long

    /**
     * 빌드 타입 · 플레이버.
     *
     * 이 모듈이 스스로 읽을 수 없는 유일한 값이다 — BuildConfig 는 컴파일 타임 상수이고
     * 그것을 생성한 모듈의 것이라, :app 이 DI 로 꽂아 준다.
     */
    fun getBuildVariant(): AppBuildVariant

    /** 처음 설치된 시각(epoch millis). 업데이트로는 바뀌지 않는다. */
    fun getFirstInstallTime(): Long

    /** 마지막으로 갱신된 시각(epoch millis). [getFirstInstallTime] 과 같으면 설치 직후다. */
    fun getLastUpdateTime(): Long

    /**
     * 설치를 수행한 패키지. 알 수 없으면 null.
     *
     * API 30+ 는 `getInstallSourceInfo`, 그 아래는 `getInstallerPackageName` 에서 온다.
     */
    fun getInstallerPackageName(): String?

    /**
     * 스토어를 거치지 않고 깔렸는가.
     *
     * adb · APK 직접 설치는 installer 가 null 이고 사내 배포 도구는 자기 패키지를 남긴다.
     * 둘 다 "우리가 낸 릴리스와 같은 바이너리인지 보증할 수 없다" 는 뜻이라,
     * 크래시·성능 통계에서 먼저 의심할 표본이다.
     */
    fun isSideloaded(): Boolean

    // --- 리소스. 저사양 여부 · 코어 수 · 저장공간은 세션 1회, 나머지는 이벤트마다. ----------------

    /**
     * 메모리. 기기 전체와 우리 힙을 한 번에 읽는다.
     *
     * 항목 하나에 함수 하나라는 규칙의 예외다 — 이 값들은 [android.app.ActivityManager] 조회
     * 한 번에서 함께 나오므로, 쪼개 두면 한 이벤트를 남기는 데 같은 바인더 호출을 네 번 한다.
     */
    fun getMemoryInfo(): MemoryInfo

    /** `ActivityManager.isLowRamDevice`. Go 에디션·저사양 기기를 OS 가 직접 알려 준다. */
    fun isLowRamDevice(): Boolean

    /** `Runtime.availableProcessors`. 프로세스가 실제로 쓸 수 있는 코어 수다. */
    fun getCpuCoreCount(): Int

    /** 배터리 잔량(0..100). 읽지 못하면 -1. */
    fun getBatteryPercent(): Int

    /** 충전 중인가. 충전 중에는 절전이 풀려 같은 코드가 더 빨리 돈다. */
    fun isCharging(): Boolean

    /** 절전 모드인가. CPU 주파수와 백그라운드 작업이 함께 눌린다. */
    fun isPowerSaveMode(): Boolean

    /** 발열 단계. API 29 아래에서는 [ThermalStatus.UNKNOWN] 이다. */
    fun getThermalStatus(): ThermalStatus

    /** 앱 저장 영역의 남은 바이트. 바닥나면 I/O 가 눈에 띄게 느려진다. */
    fun getAvailableStorageBytes(): Long

    /** 같은 영역의 전체 바이트. */
    fun getTotalStorageBytes(): Long

    // --- 네트워크. 통신사만 세션 1회, 나머지는 이벤트마다. ---------------------------------------

    /** 지금 무엇으로 붙어 있는가. 끊겨 있으면 [NetworkType.NONE]. */
    fun getNetworkType(): NetworkType

    /**
     * 종량제 회선인가(`ConnectivityManager.isActiveNetworkMetered`).
     *
     * 테더링한 Wi-Fi 도 종량제로 잡힌다 — [getNetworkType] 만 보면 놓치는 구분이라 따로 읽는다.
     */
    fun isMeteredNetwork(): Boolean

    /** 하향 대역폭 **추정치**(Kbps). OS 의 추정이라 실측이 아니다. 0 이면 모른다. */
    fun getDownstreamBandwidthKbps(): Int

    /** 상향 대역폭 추정치(Kbps). 같은 단서다. */
    fun getUpstreamBandwidthKbps(): Int

    /** 통신사 이름. SIM 이 없거나 Wi-Fi 전용 기기면 null. */
    fun getCarrierName(): String?

    // --- 식별자. 세션 1회다. ------------------------------------------------------------------

    /**
     * 우리가 만든 설치 ID(UUID). 권장하는 기본 식별자다.
     *
     * 기기가 아니라 **설치**를 가리킨다 — 앱을 지우면 사라지고 깔면 새로 생긴다.
     * 이 하나만 `suspend` 인 것은 첫 실행에 로컬 저장소에서 읽거나 새로 만들어 적어야 하기
     * 때문이다. 그 뒤로는 메모리에 든 값을 그대로 돌려준다.
     */
    suspend fun getInstallationId(): String

    /**
     * `Settings.Secure.ANDROID_ID`. 읽지 못하면 null.
     *
     * 서명 키 + 유저 단위로 나뉘고 초기화·재설치에서 달라질 수 있어 단독으로는 믿을 수 없다.
     * [getInstallationId] 가 튀었을 때 대조할 보조로만 쓴다.
     */
    fun getAndroidId(): String?
}
