package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Pairing

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
    /** Reset a stalled connection after a sync timeout; persistent writes remain queued. */
    suspend fun reconnect() {}

    /**
     * 이 우편함이 던진 실패가 서버 쪽 잘못(답하지 않음 · 내부 오류)이면 서버 오류 화면의 "오류 코드"(예: Firestore UNAVAILABLE).
     * 그 밖의 실패(권한 · 잘못된 요청 · 모르는 예외)는 null — 화면에 서버 오류로 오르지 않는다.
     */
    fun errorCode(error: Throwable): String? = null

    suspend fun put(coupleId: String, toDevice: String, item: MailItem)

    suspend fun fetch(coupleId: String, myDevice: String): List<MailItem>

    suspend fun delete(coupleId: String, myDevice: String, opId: String)

    suspend fun putControl(coupleId: String, myDevice: String, doc: ControlDoc)

    suspend fun getControl(coupleId: String, device: String): ControlDoc?

    /**
     * 앱이 살아 있는 동안의 초인종: 내 우편함에 무언가 들어오면 한 번씩 울린다(내용은 동기화가 가져간다).
     * FCM 은 앱이 꺼져 있을 때를 맡는다. 지원하지 않는 우편함은 울리지 않는다.
     */
    fun rings(coupleId: String, myDevice: String): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()

    /**
     * 서버가 아는 내 커플 id(users/{uid}.coupleId). 상대가 연결을 해제하면 서버가 지운다 — 그래서 null 이면 해제된 것.
     * 확인하지 못하면(오프라인 · 설정 전) 실패를 돌려준다. 실패는 해제로 보지 않는다.
     */
    suspend fun serverCoupleId(): Result<String?> = Result.failure(UnsupportedOperationException("확인할 수 없다"))
}

/** 첨부(그림 문서 · PNG · 사진)의 암호문 덩어리. couples/{c}/{blobId}. 받으면 지운다. */
interface BlobStore {
    suspend fun put(coupleId: String, blobId: String, bytes: ByteArray)

    suspend fun get(coupleId: String, blobId: String): ByteArray?

    suspend fun delete(coupleId: String, blobId: String)
}

/** 초대 문서 pairings/{token}. 링크를 아는 사람은 읽을 수 있다 — 토큰(128비트)이 곧 비밀이다. */
class Invite(
    val inviterUid: String,
    val deviceId: String,
    val keys: Pairing.PublicKeys,
    /** 보낸 사람 표시 이름. OG 미리보기 · 받는 쪽 화면에 보인다(이름 외 개인 정보는 싣지 않는다). */
    val name: String,
    val expireAtMs: Long,
)

/** 커플에 등록된 기기 하나(couples/{c}/devices/{d}). 연결이 끝나면 양쪽이 여기서 상대 공개키를 읽는다. */
class LinkedDevice(val uid: String, val deviceId: String, val keys: Pairing.PublicKeys)

/**
 * 초대 링크 연결. pairings/{token} (보낸 쪽이 만든다) · pairings/{token}/steps/accept (받는 쪽이 한 번 쓴다).
 * 수락 · 1회 소비 · 커플 생성은 함수(onInviteAccepted)가 한다. 웹 GET(OG 페이지)은 아무것도 바꾸지 않는다.
 */
interface PairingTransport {
    /** 같은 사람의 이전 초대는 서버(onInviteCreated)가 지운다. */
    suspend fun createInvite(token: String, invite: Invite)

    /** 없거나 이미 쓰였으면 null. 만료 여부는 [Invite.expireAtMs] 로 부른 쪽이 본다. */
    suspend fun invite(token: String): Invite?

    suspend fun accept(token: String, uid: String, deviceId: String, keys: Pairing.PublicKeys)

    /** 이 토큰으로 서버가 만든 내 커플 id(users/{myUid}.linkedVia == token 일 때만). 아직이면 null. */
    suspend fun linkedCoupleId(token: String, myUid: String): String?

    /** 커플의 상대 기기(내 uid 가 아닌 활성 기기). */
    suspend fun partnerDevice(coupleId: String, myUid: String): LinkedDevice?
}
