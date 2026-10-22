package com.sadturtleman.flowershoes.core.data.sync

/**
 * 서버(Firestore · Storage)와 주고받는 것의 계약. 실제 구현은 Firebase 어댑터, 테스트는 메모리 구현이다.
 * 여기를 지나는 것은 전부 암호문이거나 공개해도 되는 값(공개키 · 커밋 · kind)이다.
 */

/** 우편함 문서 하나. couples/{c}/inbox/{받는 기기}/ops/{opId} */
class MailItem(
    val opId: String,
    val kind: String,
    val from: String,
    val header: ByteArray,
    val ciphertext: ByteArray,
    val signature: ByteArray,
    val resend: Boolean = false,
)

/** 커서 알림(제어 메시지). couples/{c}/control/{기기} 하나를 덮어쓴다. */
class ControlDoc(val payload: ByteArray, val signature: ByteArray)

interface Mailbox {
    suspend fun put(coupleId: String, toDevice: String, item: MailItem)

    suspend fun fetch(coupleId: String, myDevice: String): List<MailItem>

    suspend fun delete(coupleId: String, myDevice: String, opId: String)

    suspend fun putControl(coupleId: String, myDevice: String, doc: ControlDoc)

    suspend fun getControl(coupleId: String, device: String): ControlDoc?
}

/** 첨부(그림 문서 · PNG · 사진)의 암호문 덩어리. couples/{c}/{blobId}. 받으면 지운다. */
interface BlobStore {
    suspend fun put(coupleId: String, blobId: String, bytes: ByteArray)

    suspend fun get(coupleId: String, blobId: String): ByteArray?

    suspend fun delete(coupleId: String, blobId: String)
}

/** 연결 단계 문서. pairings/{code}/steps/{name} */
interface PairingTransport {
    suspend fun createInvite(code: String, inviterUid: String)

    /** pairings/{code}.uid — 초대받은 쪽이 상대 uid 를 안다. */
    suspend fun inviterUid(code: String): String?

    suspend fun putStep(code: String, name: String, fields: Map<String, String>)

    /** 단계가 올라올 때까지 기다리지 않고 지금 값만. 없으면 null. */
    suspend fun getStep(code: String, name: String): Map<String, String>?

    /** 양쪽 확인이 끝나 서버(함수)가 만든 커플 id. 아직이면 null. */
    suspend fun linkedCoupleId(code: String): String?

    /** 서버가 커플을 만들 때 초대받은 기기 앞으로 둔 커플 키 봉투. */
    suspend fun wrappedKeyFor(coupleId: String, deviceId: String): ByteArray?
}

object Steps {
    const val JOIN = "join"
    const val INVITER = "inviter"
    const val REVEAL = "reveal"
    const val CONFIRM_INVITER = "confirm-inviter"
    const val CONFIRM_JOINER = "confirm-joiner"
}
