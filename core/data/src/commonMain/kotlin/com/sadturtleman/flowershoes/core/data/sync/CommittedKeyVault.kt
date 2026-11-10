package com.sadturtleman.flowershoes.core.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.Signatures
import com.sadturtleman.flowershoes.core.crypto.sealTo
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 새 관계의 활성 키와 관계·기기 행은 같은 SQLite 커밋으로 공개한다.
 * DB에는 이 기기 비밀키로만 여는 봉투를 둔다. 비밀키는 기존 Keystore/Keychain에 남는다.
 * 구버전 관계(sealedKeys == null)는 기존 보관함을 그대로 읽는다.
 */
class CommittedKeyVault internal constructor(
    private val db: AppDatabase,
    private val identity: Identity,
    val platform: KeyVault,
) : KeyVault by platform {
    @Serializable
    private data class Keys(val coupleId: String, val ownerUid: String, val deviceId: String, val epochs: Map<Int, String>)

    /** 반환한 행을 기기 행들과 같은 트랜잭션에서 저장하기 전에는 활성 키가 생기지 않는다. */
    suspend fun sealRelationship(couple: CoupleEntity, key: CoupleKey): CoupleEntity {
        check(couple.myUserId == identity.userId && couple.partnerUserId.isNotBlank())
        return couple.copy(sealedKeys = seal(Keys(couple.id, identity.userId, identity.deviceId, mapOf(key.epoch to key.export().b64()))))
    }

    private suspend fun seal(keys: Keys): String {
        val device = checkNotNull(platform.deviceKeys()) { "기기 키를 확인할 수 없어요" }
        val body = Json.encodeToString(Keys.serializer(), keys).encodeToByteArray()
        return sealTo(device.boxPub, device.sign(body) + body).b64()
    }

    private suspend fun open(couple: CoupleEntity): Keys {
        try {
            val device = checkNotNull(platform.deviceKeys())
            val plain = device.openSealed(checkNotNull(couple.sealedKeys).unb64())
            check(plain.size > 64)
            val body = plain.copyOfRange(64, plain.size)
            check(Signatures.verify(body, plain.copyOf(64), device.signPub))
            val keys = Json.decodeFromString(Keys.serializer(), body.decodeToString())
            check(keys.coupleId == couple.id && keys.ownerUid == couple.myUserId)
            check(keys.ownerUid == identity.userId && keys.deviceId == identity.deviceId)
            check(keys.epochs.isNotEmpty() && keys.epochs.all { (epoch, value) -> epoch > 0 && value.unb64().size == 32 })
            return keys
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 파싱 예외에 키 원문이 들어갈 수 있으므로 원인을 노출하지 않는다.
            throw IllegalStateException("현재 관계의 기기 키를 확인할 수 없어요")
        }
    }

    override suspend fun currentCoupleKey(): CoupleKey? {
        val couple = db.coupleDao().get()
        if (couple != null && (couple.myUserId != identity.userId || couple.partnerUserId.isBlank() || couple.sealedKeys == "")) return null
        if (couple?.sealedKeys == null) return platform.currentCoupleKey()
        if (couple.myUserId != identity.userId || couple.partnerUserId.isBlank()) return null
        val keys = open(couple)
        return keys.epochs[couple.keyEpoch]?.let { CoupleKey(couple.keyEpoch, it.unb64()) }
    }

    override suspend fun coupleKey(epoch: Int): CoupleKey? {
        val couple = db.coupleDao().get()
        if (couple != null && (couple.myUserId != identity.userId || couple.partnerUserId.isBlank() || couple.sealedKeys == "")) return null
        if (couple?.sealedKeys == null) return platform.coupleKey(epoch)
        if (couple.myUserId != identity.userId || couple.partnerUserId.isBlank() || epoch > couple.keyEpoch) return null
        return open(couple).epochs[epoch]?.let { CoupleKey(epoch, it.unb64()) }
    }

    /** 확장 프로세스용 사본. 호출자는 관계를 읽고 게시할 때까지 같은 DB 쓰기 경계를 유지한다. */
    suspend fun sharedCoupleKeys(): List<CoupleKey> {
        val couple = checkNotNull(db.coupleDao().get())
        return open(couple).epochs.filterKeys { it <= couple.keyEpoch }.map { (epoch, value) -> CoupleKey(epoch, value.unb64()) }
    }

    override suspend fun putCoupleKey(key: CoupleKey) = db.useWriterConnection { tx -> tx.immediateTransaction {
        val couple = db.coupleDao().get()
        if (couple?.sealedKeys == null) platform.putCoupleKey(key)
        else {
            val keys = open(couple)
            db.coupleDao().upsert(couple.copy(sealedKeys = seal(keys.copy(epochs = keys.epochs + (key.epoch to key.export().b64())))))
        }
    } }

    override suspend fun dropEpochsBefore(epoch: Int) = db.useWriterConnection { tx -> tx.immediateTransaction {
        val couple = db.coupleDao().get()
        if (couple?.sealedKeys == null) platform.dropEpochsBefore(epoch)
        else {
            val keys = open(couple)
            check(epoch <= couple.keyEpoch)
            db.coupleDao().upsert(couple.copy(sealedKeys = seal(keys.copy(epochs = keys.epochs.filterKeys { it >= epoch }))))
        }
    } }

    override suspend fun clearCoupleKeys() {
        db.useWriterConnection { tx -> tx.immediateTransaction {
            db.coupleDao().get()?.takeIf { it.sealedKeys != null }?.let { db.coupleDao().upsert(it.copy(sealedKeys = "")) }
        } }
        platform.clearCoupleKeys()
    }

    override suspend fun clearAll() {
        clearCoupleKeys()
        platform.clearAll()
    }
}

fun KeyVault.forDatabase(db: AppDatabase, identity: Identity): CommittedKeyVault =
    CommittedKeyVault(db, identity, platformVault)

val KeyVault.platformVault: KeyVault get() = if (this is CommittedKeyVault) platform else this
