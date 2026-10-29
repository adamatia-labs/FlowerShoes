package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys

/**
 * 비밀 키 보관함. 기기 키 · 커플 키(epoch 별)는 이 기기에만 둔다(ThisDeviceOnly, 백업 · 동기화 안 함).
 *
 * 실제 구현은 Android Keystore 로 감싼 파일 · iOS Keychain(access group 공유)이다.
 * 옛 epoch 키는 아직 안 받은 Op 을 풀기 위해 31일 보관한 뒤 [dropEpochsBefore] 로 지운다.
 */
interface KeyVault {
    suspend fun deviceKeys(): DeviceKeys?

    suspend fun putDeviceKeys(keys: DeviceKeys)

    suspend fun coupleKey(epoch: Int): CoupleKey?

    suspend fun currentCoupleKey(): CoupleKey?

    suspend fun putCoupleKey(key: CoupleKey)

    suspend fun dropEpochsBefore(epoch: Int)

    /**
     * 기기 교체 승인 중(#20): 새 폰 앞으로 봉인해 보낸 새 커플 키와 그때의 옛 기기 · cutoff.
     * 아직 현재 키가 아니다 — 함수가 승인을 받아들이면 [putCoupleKey] 로 옮기고 비운다. 앱이 꺼져도 남아야 한다.
     */
    suspend fun pendingApproval(): PendingApproval?

    suspend fun putPendingApproval(approval: PendingApproval?)

    /** 연결 해제 · 탈퇴: 커플 키 전부(승인 중인 키도). */
    suspend fun clearCoupleKeys()

    /** 탈퇴: 기기 키 · 커플 키 전부. */
    suspend fun clearAll()
}

/** 승인 중인 기기 교체. [key] 는 새 epoch 커플 키. */
class PendingApproval(val requestId: String, val key: CoupleKey, val oldDeviceId: String?, val cutoff: Map<String, Long>)

/** 보관함 파일에 두는 모양(Android · iOS 가 같은 JSON 을 쓴다). */
@kotlinx.serialization.Serializable
internal data class StoredApproval(val requestId: String, val epoch: Int, val key: String, val oldDeviceId: String? = null, val cutoff: Map<String, Long> = emptyMap()) {
    fun toModel() = PendingApproval(requestId, CoupleKey(epoch, key.unb64()), oldDeviceId, cutoff)

    companion object {
        fun of(p: PendingApproval) = StoredApproval(p.requestId, p.key.epoch, p.key.export().b64(), p.oldDeviceId, p.cutoff)
    }
}

/** 플랫폼 보관함이 붙기 전 · 테스트용. 프로세스가 끝나면 사라진다. */
class InMemoryKeyVault : KeyVault {
    private var device: DeviceKeys? = null
    private val couple = mutableMapOf<Int, CoupleKey>()
    private var pending: PendingApproval? = null

    override suspend fun deviceKeys() = device

    override suspend fun putDeviceKeys(keys: DeviceKeys) {
        device = keys
    }

    override suspend fun coupleKey(epoch: Int) = couple[epoch]

    override suspend fun currentCoupleKey() = couple.maxByOrNull { it.key }?.value

    override suspend fun putCoupleKey(key: CoupleKey) {
        couple[key.epoch] = key
    }

    override suspend fun dropEpochsBefore(epoch: Int) {
        couple.keys.removeAll { it < epoch }
    }

    override suspend fun pendingApproval() = pending

    override suspend fun putPendingApproval(approval: PendingApproval?) {
        pending = approval
    }

    override suspend fun clearCoupleKeys() {
        couple.clear()
        pending = null
    }

    override suspend fun clearAll() {
        device = null
        couple.clear()
        pending = null
    }
}
