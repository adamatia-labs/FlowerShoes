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

    /** 연결 해제 · 탈퇴: 커플 키 전부. */
    suspend fun clearCoupleKeys()

    /** 탈퇴: 기기 키 · 커플 키 전부. */
    suspend fun clearAll()
}

/** 플랫폼 보관함이 붙기 전 · 테스트용. 프로세스가 끝나면 사라진다. */
class InMemoryKeyVault : KeyVault {
    private var device: DeviceKeys? = null
    private val couple = mutableMapOf<Int, CoupleKey>()

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

    override suspend fun clearCoupleKeys() = couple.clear()

    override suspend fun clearAll() {
        device = null
        couple.clear()
    }
}
