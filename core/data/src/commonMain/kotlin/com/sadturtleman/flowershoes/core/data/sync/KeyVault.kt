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

    /** 백업에 넣을 커플 키 전부(현재 + 31일 보관 중인 직전 epoch). */
    suspend fun allCoupleKeys(): List<CoupleKey>

    /**
     * 이 기기의 백업 키(BK) 사본과 복구 코드. 플랫폼 동기화(iCloud 키체인 · Block Store)와 별개로 이 기기에도 둔다 —
     * 설정 › 백업 › 복구 코드에서 보여 주기 위해서다.
     */
    suspend fun backupSecrets(): Pair<ByteArray, String>?

    suspend fun putBackupSecrets(backupKey: ByteArray, recoveryCode: String)

    suspend fun putCoupleKey(key: CoupleKey)

    suspend fun dropEpochsBefore(epoch: Int)

    /** 연결 해제 · 탈퇴: 커플 키 전부. */
    suspend fun clearCoupleKeys()
}

/** 플랫폼 보관함이 붙기 전 · 테스트용. 프로세스가 끝나면 사라진다. */
class InMemoryKeyVault : KeyVault {
    private var device: DeviceKeys? = null
    private val couple = mutableMapOf<Int, CoupleKey>()
    private var backup: Pair<ByteArray, String>? = null

    override suspend fun allCoupleKeys() = couple.values.sortedBy { it.epoch }

    override suspend fun backupSecrets() = backup

    override suspend fun putBackupSecrets(backupKey: ByteArray, recoveryCode: String) {
        backup = backupKey to recoveryCode
    }

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
}
