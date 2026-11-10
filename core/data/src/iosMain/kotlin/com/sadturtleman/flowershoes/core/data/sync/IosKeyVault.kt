@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.Sodium
import kotlinx.coroutines.CancellationException
import libsodium.sodium_init

/** Shared Keychain state, serialized across instances and processes by an App Group file lock. */
class IosKeyVault private constructor(storage: VaultStorage) : KeyVault {
    constructor(accessGroup: String? = defaultAccessGroup()) : this(IosVaultStorage(IosKeychainStore(accessGroup)))
    internal constructor(accessGroup: String?, keychain: IosKeychainStore) : this(IosVaultStorage(keychain))
    internal constructor(items: VaultItems, lockDirectory: String) : this(IosVaultStorage(items, lockDirectory))

    private val state = SerializedKeyVault(storage) {
        try { Sodium.awaitInit() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalStateException) { check(sodium_init() == 1) }
        check(sodium_init() >= 0)
    }

    override suspend fun deviceKeys() = state.deviceKeys()
    override suspend fun putDeviceKeys(keys: DeviceKeys) = state.putDeviceKeys(keys)
    override suspend fun coupleKey(epoch: Int) = state.coupleKey(epoch)
    override suspend fun currentCoupleKey() = state.currentCoupleKey()
    override suspend fun putCoupleKey(key: CoupleKey) = state.putCoupleKey(key)
    override suspend fun dropEpochsBefore(epoch: Int) = state.dropEpochsBefore(epoch)
    override suspend fun pendingApproval() = state.pendingApproval()
    override suspend fun putPendingApproval(approval: PendingApproval?) = state.putPendingApproval(approval)
    override suspend fun clearCoupleKeys() = state.clearCoupleKeys()
    override suspend fun clearAll() = state.clearAll()
    suspend fun putPartnerSignPub(pub: ByteArray) = state.putPartnerSignPub(pub)
    suspend fun putSharedRelationship(scope: String, keys: List<CoupleKey>, partnerPub: ByteArray, device: DeviceKeys) =
        state.putSharedRelationship(scope, keys, partnerPub, device)

    companion object {
        const val ACCESS_GROUP = "com.sadturtleman.kkotsin.shared"
        private fun defaultAccessGroup(): String? =
            (platform.Foundation.NSBundle.mainBundle.objectForInfoDictionaryKey("KkotsinKeychainAccessGroup") as? String)
                ?.takeIf { it.endsWith(".$ACCESS_GROUP") }
    }
}
