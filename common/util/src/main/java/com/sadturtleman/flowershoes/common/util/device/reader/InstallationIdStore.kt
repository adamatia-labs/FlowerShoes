package com.sadturtleman.flowershoes.common.util.device.reader

import com.sadturtleman.flowershoes.common.datastore.DataStorage
import com.sadturtleman.flowershoes.common.datastore.PreferenceKey
import java.util.UUID

/**
 * 설치 ID 를 만들어 두고 다시 꺼내 주는 곳.
 *
 * ANDROID_ID 를 쓰지 않고 직접 만드는 이유는 그쪽이 우리 것이 아니기 때문이다 —
 * 서명 키와 유저 단위로 나뉘고, 초기화·재설치에서 달라지며, 언제 다시 규칙이 바뀔지도
 * 우리가 정하지 못한다. 직접 만든 UUID 는 "앱을 지우면 사라지고 깔면 새로 생긴다" 는
 * 규칙 하나로 끝난다.
 *
 * 읽기와 쓰기를 [DataStorage.update] 한 번으로 묶는다. 읽고 → 없으면 → 쓰는 세 걸음으로
 * 쪼개면 앱 시작 직후처럼 여러 코루틴이 동시에 부르는 순간에 서로 다른 UUID 를 만들어
 * 뒤에 쓴 쪽이 이긴다. 그러면 같은 설치가 두 사람으로 세어진다.
 */
internal class InstallationIdStore constructor(
    private val storage: DataStorage,
) {

    suspend fun installationId(): String {
        var resolved = ""
        storage.update(KEY) { saved -> (saved ?: UUID.randomUUID().toString()).also { resolved = it } }
        return resolved
    }

    private companion object {
        val KEY = PreferenceKey.StringKey("installation_id")
    }
}
