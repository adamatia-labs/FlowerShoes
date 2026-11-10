package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A storage transaction must serialize readers/writers across every instance and process. */
internal interface VaultStorage {
    suspend fun <T> transaction(block: suspend VaultItems.() -> T): T
}

internal interface VaultItems {
    fun read(account: String): ByteArray?
    fun write(account: String, bytes: ByteArray)
    fun delete(account: String)
    fun accounts(): Set<String>
}

/** Canonical state is committed before compatibility mirrors. A persisted repair intent survives process death. */
internal class SerializedKeyVault(
    private val storage: VaultStorage,
    private val initializeCrypto: suspend () -> Unit,
) : KeyVault {
    @Serializable
    private data class State(
        val formatVersion: Int = 1,
        val signPub: String? = null,
        val boxPub: String? = null,
        val deviceSecret: String? = null,
        val coupleKeys: Map<Int, String> = emptyMap(),
        val pendingApproval: StoredApproval? = null,
        val partnerSignPub: String? = null,
        val sharedRelationship: SharedKeys? = null,
        val mirrorAccounts: Set<String> = emptySet(),
        val repairMirrors: Boolean = false,
    )

    @Serializable
    private data class SharedKeys(val scope: String, val values: Map<String, String>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private suspend fun validate(s: State) {
        check(s.formatVersion in 1..2)
        val present = listOf(s.signPub, s.boxPub, s.deviceSecret).count { it != null }
        check(present == 0 || present == 3)
        if (present == 0) check(s.coupleKeys.isEmpty() && s.pendingApproval == null && s.partnerSignPub == null && s.sharedRelationship == null)
        if (present == 3) {
            initializeCrypto()
            val sign = s.signPub!!.unb64(); val box = s.boxPub!!.unb64(); val secret = s.deviceSecret!!.unb64()
            check(sign.size == 32 && box.size == 32 && secret.size == 96)
            val keys = DeviceKeys.import(sign, box, secret)
            val challenge = "kkotsin-key-validation-v1".encodeToByteArray()
            check(Signatures.verify(challenge, keys.sign(challenge), sign))
            check(keys.openSealed(sealTo(box, challenge)).contentEquals(challenge))
        }
        s.coupleKeys.forEach { (epoch, key) -> check(epoch > 0 && key.unb64().size == 32) }
        s.pendingApproval?.let { p ->
            check(p.requestId.isNotBlank() && p.epoch > 0 && p.key.unb64().size == 32)
            check(p.cutoff.all { (stream, seq) -> stream.isNotBlank() && seq >= 0 })
        }
        s.partnerSignPub?.let { check(it.unb64().size == 32) }
        s.sharedRelationship?.let { shared ->
            check(shared.scope.isNotBlank())
            check(shared.values["device.signSecret"]?.unb64()?.contentEquals(s.deviceSecret!!.unb64().copyOf(64)) == true)
            check(shared.values["partner.signPub"]?.unb64()?.size == 32)
            val keys = shared.values.filterKeys { it.startsWith("coupleKey.") }
            check(keys.isNotEmpty() && keys.all { (name, value) -> isEpochMirror(name) && value.unb64().size == 32 })
            check(shared.values.keys == keys.keys + setOf("device.signSecret", "partner.signPub"))
        }
        check(s.mirrorAccounts.all(::isMirror))
    }

    private suspend fun VaultItems.load(repairFirst: Boolean = true): State {
        val state = try {
            read(ACCOUNT)?.let { json.decodeFromString(State.serializer(), it.decodeToString()) } ?: State()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error("키 보관 정보를 읽지 못했어요") }
        try { validate(state) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error("키 보관 정보를 읽지 못했어요") }
        return if (repairFirst && state.repairMirrors) repair(state) else state
    }

    private fun mirrors(s: State): Map<String, ByteArray> = buildMap {
        s.deviceSecret?.let { put("device.signSecret", it.unb64().copyOf(64)) }
        s.coupleKeys.forEach { (epoch, key) -> put("coupleKey.$epoch", key.unb64()) }
        s.partnerSignPub?.let { put("partner.signPub", it.unb64()) }
        s.sharedRelationship?.let { put("relationship.keys", json.encodeToString(SharedKeys.serializer(), it).encodeToByteArray()) }
    }

    private fun VaultItems.repair(s: State): State {
        val wanted = mirrors(s)
        wanted.forEach { (account, bytes) -> write(account, bytes) }
        (s.mirrorAccounts - wanted.keys).forEach(::delete)
        val clean = s.copy(mirrorAccounts = wanted.keys, repairMirrors = false)
        write(ACCOUNT, json.encodeToString(State.serializer(), clean).encodeToByteArray())
        return clean
    }

    private suspend fun VaultItems.commit(previous: State, next: State, clearUnknown: Boolean = false) {
        // A legacy orphan may be the last surviving copy of a lost epoch. Do not activate or erase it on an ordinary write.
        val known = previous.mirrorAccounts + mirrors(previous).keys + if (clearUnknown) accounts().filter(::isMirror) else emptyList()
        val desired = next.copy(formatVersion = 2, mirrorAccounts = known + mirrors(next).keys, repairMirrors = true)
        try { validate(desired) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error("키 보관 정보를 저장하지 못했어요") }
        write(ACCOUNT, json.encodeToString(State.serializer(), desired).encodeToByteArray())
        repair(desired)
    }

    /** The old primary did not include its mirrors. Import them once, inside the same storage lock. */
    private fun VaultItems.upgrade(s: State): State = try {
        if (s.formatVersion == 2 || s.deviceSecret == null) return s
        val partner = read("partner.signPub")?.also { check(it.size == 32) }?.b64()
        val shared = read("relationship.keys")?.let { json.decodeFromString(SharedKeys.serializer(), it.decodeToString()) }
            ?.takeIf { it.values["device.signSecret"] == s.deviceSecret.unb64().copyOf(64).b64() }
        s.copy(partnerSignPub = partner, sharedRelationship = shared)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { error("키 보관 사본을 확인하지 못했어요") }

    private suspend fun change(clearUnknown: Boolean = false, block: (State) -> State) = storage.transaction {
        val old = load(repairFirst = false)
        val prepared = upgrade(old)
        val next = block(prepared)
        val replacedDevice = old.signPub != null && (next.signPub != old.signPub || next.boxPub != old.boxPub)
        commit(old, next, clearUnknown || replacedDevice)
    }

    override suspend fun deviceKeys(): DeviceKeys? = storage.transaction {
        val s = load()
        if (s.deviceSecret == null) null else DeviceKeys.import(s.signPub!!.unb64(), s.boxPub!!.unb64(), s.deviceSecret.unb64())
    }

    override suspend fun putDeviceKeys(keys: DeviceKeys) = change { old ->
        val current = if (old.signPub != null && (old.signPub != keys.signPub.b64() || old.boxPub != keys.boxPub.b64())) State() else old
        current.copy(signPub = keys.signPub.b64(), boxPub = keys.boxPub.b64(), deviceSecret = keys.exportSecret().b64())
    }
    override suspend fun coupleKey(epoch: Int): CoupleKey? = storage.transaction { load().coupleKeys[epoch]?.let { CoupleKey(epoch, it.unb64()) } }
    override suspend fun currentCoupleKey(): CoupleKey? = storage.transaction { load().coupleKeys.maxByOrNull { it.key }?.let { CoupleKey(it.key, it.value.unb64()) } }
    override suspend fun putCoupleKey(key: CoupleKey) = change { it.copy(coupleKeys = it.coupleKeys + (key.epoch to key.export().b64())) }
    override suspend fun dropEpochsBefore(epoch: Int) = change { it.copy(coupleKeys = it.coupleKeys.filterKeys { e -> e >= epoch }) }
    override suspend fun pendingApproval(): PendingApproval? = storage.transaction { load().pendingApproval?.toModel() }
    override suspend fun putPendingApproval(approval: PendingApproval?) = change { it.copy(pendingApproval = approval?.let(StoredApproval::of)) }
    override suspend fun clearCoupleKeys() = change(clearUnknown = true) { it.copy(coupleKeys = emptyMap(), pendingApproval = null, partnerSignPub = null, sharedRelationship = null) }
    override suspend fun clearAll() = storage.transaction { val previous = load(repairFirst = false); commit(previous, State(), clearUnknown = true) }

    suspend fun putPartnerSignPub(pub: ByteArray) = change { check(pub.size == 32); it.copy(partnerSignPub = pub.b64()) }

    suspend fun putSharedRelationship(scope: String, keys: List<CoupleKey>, partnerPub: ByteArray, device: DeviceKeys) = change { s ->
        check(s.signPub == device.signPub.b64() && s.boxPub == device.boxPub.b64()) { "기기 키가 바뀌었어요" }
        val values = keys.associate { "coupleKey.${it.epoch}" to it.export().b64() } + mapOf(
            "partner.signPub" to partnerPub.b64(), "device.signSecret" to device.exportSecret().copyOf(64).b64(),
        )
        s.copy(sharedRelationship = SharedKeys(scope, values))
    }

    companion object {
        const val ACCOUNT = "kkotsin.vault"
        private fun isEpochMirror(account: String) = account.startsWith("coupleKey.") && account.removePrefix("coupleKey.").toIntOrNull()?.let { it > 0 } == true
        private fun isMirror(account: String) = account in setOf("device.signSecret", "partner.signPub", "relationship.keys") || isEpochMirror(account)
    }
}
