package com.sadturtleman.flowershoes.core.featureflag

/**
 * 앱을 쓰게 둘 것인가(공통 상태 · 업데이트 필요 / 점검 중, #72). 네트워크 없음 · 서버 오류는 앱 안에서 판단하므로 여기 없다.
 *
 * 리모트 값이 없거나 읽지 못하면 언제나 [Open] 이다 — 잘못 막으면 앱을 아예 쓸 수 없게 된다.
 */
sealed interface AppGate {
    data object Open : AppGate

    /** 스토어에서 [minVersion] 이상을 받아야 한다. */
    data class UpdateRequired(val minVersion: String) : AppGate

    data object Maintenance : AppGate
}

/**
 * [FlagKey.MinVersion] · [FlagKey.Maintenance] 를 읽어 지금 앱을 막을지 정한다.
 * 둘 다 stable 이 아니라 부를 때마다 리모트에 다녀온다(실패하면 마지막으로 성공한 값).
 *
 * 겹치면 업데이트가 먼저다 — 점검 중에도 새 버전은 받을 수 있다.
 *
 * @param appVersion 이 앱의 버전 이름. "1.2.0-dev" 처럼 꼬리가 붙어 있어도 된다.
 */
suspend fun FeatureFlagProvider.appGate(appVersion: String): AppGate {
    val min = get(FlagKey.MinVersion).trim()
    val current = versionParts(appVersion)
    val required = versionParts(min)
    if (current != null && required != null && compareVersions(current, required) < 0) return AppGate.UpdateRequired(min)
    return if (get(FlagKey.Maintenance)) AppGate.Maintenance else AppGate.Open
}

/** "2.10.1-dev" → [2, 10, 1]. 숫자로 이뤄지지 않았으면 null(비교하지 않는다). */
private fun versionParts(version: String): List<Int>? {
    val core = version.trim().substringBefore('-').substringBefore('+')
    if (core.isEmpty()) return null
    return core.split('.').map { it.toIntOrNull() ?: return null }
}

/** 자리마다 숫자로 비교한다. 모자란 자리는 0 이다("2.1" == "2.1.0"). */
private fun compareVersions(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}
