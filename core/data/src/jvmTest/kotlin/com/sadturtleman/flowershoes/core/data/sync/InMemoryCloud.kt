package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Pairing

/**
 * 테스트용 서버. Firestore · Storage · Cloud Function(onInviteAccepted 등) 이 하는 일을 메모리에서 흉내 낸다.
 * [tamper] 로 "서버가 암호문을 바꿔치기하는" 상황도 만든다.
 */
class InMemoryCloud : Mailbox, BlobStore, PairingTransport, RotationTransport, AccountTransport {
    /** 넘겨주기(relinks/…)는 초대(pairings/…)와 다른 컬렉션이다. */
    val relinks = RelinkCloud()

    private val inbox = mutableMapOf<String, MutableMap<String, MailItem>>() // "{c}/{device}" → opId → item
    private val control = mutableMapOf<String, ControlDoc>()
    private val blobStore = mutableMapOf<String, ByteArray>()
    private val invites = mutableMapOf<String, Invite>()
    private val accepted = mutableSetOf<String>()
    private val linked = mutableMapOf<String, String>()
    private val coupleDevices = mutableMapOf<String, List<LinkedDevice>>()
    private val members = mutableSetOf<String>()

    var tamper: ((MailItem) -> MailItem)? = null

    fun inboxSize(coupleId: String, device: String) = inbox["$coupleId/$device"]?.size ?: 0

    // Mailbox
    override suspend fun put(coupleId: String, toDevice: String, item: MailItem) {
        check(item.ciphertext.size <= MAX_OP_CIPHERTEXT) { "우편함 규칙: 암호문 ${item.ciphertext.size}바이트 > $MAX_OP_CIPHERTEXT" }
        inbox.getOrPut("$coupleId/$toDevice") { linkedMapOf() }[item.opId] = tamper?.invoke(item) ?: item
    }

    override suspend fun fetch(coupleId: String, myDevice: String) = inbox["$coupleId/$myDevice"]?.values?.toList().orEmpty()

    override suspend fun delete(coupleId: String, myDevice: String, opId: String) {
        inbox["$coupleId/$myDevice"]?.remove(opId)
    }

    override suspend fun putControl(coupleId: String, myDevice: String, doc: ControlDoc) {
        control["$coupleId/$myDevice"] = doc
    }

    override suspend fun getControl(coupleId: String, device: String) = control["$coupleId/$device"]

    // BlobStore
    override suspend fun put(coupleId: String, blobId: String, bytes: ByteArray) {
        check(bytes.size <= MAX_BLOB) { "Storage 규칙: blob ${bytes.size}바이트 > $MAX_BLOB" }
        blobStore["$coupleId/$blobId"] = bytes
    }

    override suspend fun get(coupleId: String, blobId: String) = blobStore["$coupleId/$blobId"]

    override suspend fun delete(coupleId: String, blobId: String) {
        blobStore.remove("$coupleId/$blobId")
    }

    fun blobCount() = blobStore.size

    // PairingTransport — 함수 onInviteCreated · onInviteAccepted 와 같은 규칙.
    private class Accept(val uid: String, val deviceId: String, val keys: Pairing.PublicKeys)

    /** 수락 함수를 잠시 멈춘다(받는 쪽의 "기다리는 중" 상태를 보려고). */
    var holdAccepts = false
    private val held = mutableListOf<Pair<String, Accept>>()

    /** 수락 푸시를 받은 보낸 사람 uid. */
    val acceptedPushes = mutableListOf<String>()

    override suspend fun createInvite(token: String, invite: Invite) {
        check(token !in invites) { "같은 토큰 문서는 한 번만 만든다" }
        // onInviteCreated: 같은 사람의 이전 초대를 지운다.
        invites.values.removeAll { it.inviterUid == invite.inviterUid }
        invites[token] = invite
    }

    override suspend fun invite(token: String) = invites[token]

    override suspend fun accept(token: String, uid: String, deviceId: String, keys: Pairing.PublicKeys) {
        check(token in invites) { "규칙: 초대 문서가 있어야 수락한다" }
        check(token !in accepted) { "규칙: 수락은 한 번만" }
        accepted += token
        val a = Accept(uid, deviceId, keys)
        if (holdAccepts) held += token to a else onInviteAccepted(token, a)
    }

    fun releaseAccepts() {
        holdAccepts = false
        held.toList().also { held.clear() }.forEach { (t, a) -> onInviteAccepted(t, a) }
    }

    override suspend fun linkedCoupleId(token: String, myUid: String) = linked["$token/$myUid"]

    override suspend fun partnerDevice(coupleId: String, myUid: String) = coupleDevices[coupleId]?.firstOrNull { it.uid != myUid }

    private fun onInviteAccepted(token: String, a: Accept) {
        val invite = invites[token] ?: return
        val expired = invite.expireAtMs <= kotlin.time.Clock.System.now().toEpochMilliseconds()
        if (expired || invite.inviterUid in members || a.uid in members) {
            invites.remove(token)
            return
        }
        val coupleId = "couple-$token"
        coupleDevices[coupleId] = listOf(LinkedDevice(invite.inviterUid, invite.deviceId, invite.keys), LinkedDevice(a.uid, a.deviceId, a.keys))
        members += invite.inviterUid
        members += a.uid
        linked["$token/${invite.inviterUid}"] = coupleId
        linked["$token/${a.uid}"] = coupleId
        invites.values.removeAll { it.inviterUid == invite.inviterUid || it.inviterUid == a.uid }
        acceptedPushes += invite.inviterUid
    }

    // RotationTransport — 서버(onRotationApproved · finalizeDevice)와 같은 확인을 한다.
    private val pendingDevices = mutableMapOf<String, Pair<String, ByteArray>>() // deviceId → (uid, signPub)
    private val rotationDocs = mutableMapOf<String, MutableList<RotationRequest>>()
    private val approvals = mutableMapOf<String, RotationApproval>()

    override suspend fun putPendingDevice(coupleId: String, deviceId: String, uid: String, signPub: ByteArray, boxPub: ByteArray) {
        pendingDevices[deviceId] = uid to signPub
    }

    override suspend fun putRotation(coupleId: String, request: RotationRequest) {
        rotationDocs.getOrPut(coupleId) { mutableListOf() } += request
    }

    override suspend fun rotations(coupleId: String) = rotationDocs[coupleId].orEmpty()

    override suspend fun putApproval(coupleId: String, rotationId: String, approval: RotationApproval) {
        approvals["$coupleId/$rotationId"] = approval
    }

    override suspend fun approval(coupleId: String, rotationId: String) = approvals["$coupleId/$rotationId"]

    override suspend fun finalize(coupleId: String, rotationId: String, challengeSig: ByteArray): String {
        val r = rotationDocs[coupleId]?.firstOrNull { it.id == rotationId } ?: error("요청 없음")
        checkNotNull(approvals["$coupleId/$rotationId"]) { "상대가 아직 확인하지 않았다" }
        val ok = com.sadturtleman.flowershoes.core.crypto.Signatures.verify(RotationMessages.finalize(coupleId, rotationId, r.uid), challengeSig, r.newSignPub)
        check(ok) { "새 기기 서명이 맞지 않는다" }
        return "custom-token-for-${r.uid}"
    }

    // AccountTransport — 함수 unlinkCouple 처럼 커플의 서버 데이터를 지운다.
    val unlinked = mutableListOf<String>()

    override suspend fun unlinkCouple(coupleId: String) {
        unlinked += coupleId
        coupleDevices.remove(coupleId)?.forEach { members -= it.uid }
        inbox.keys.removeAll { it.startsWith("$coupleId/") }
        control.keys.removeAll { it.startsWith("$coupleId/") }
        blobStore.keys.removeAll { it.startsWith("$coupleId/") }
    }

    override suspend fun deleteAccount() {
        unlinked += "account"
    }

    companion object {
        /** firestore.rules 우편함: ct(base64) ≤ 90,000자 = 암호문 67,500바이트. */
        const val MAX_OP_CIPHERTEXT = 90_000 / 4 * 3

        /** storage.rules couples/{c}/…: 10MB. 이보다 작은 것(≤ 675,000)은 Firestore blob 문서에 인라인으로 간다. */
        const val MAX_BLOB = 10 * 1024 * 1024
    }
}

/** relinks/{code} 와 함수 onRelinkConfirmed(두 확인 · 같은 SAS → 새 기기 활성화 · 키 봉투)를 흉내 낸다. */
class RelinkCloud : RelinkTransport {
    private val docs = mutableMapOf<String, Pair<String, String>>() // code → (uid, coupleId)
    private val steps = mutableMapOf<String, MutableMap<String, Map<String, String>>>()
    private val active = mutableSetOf<String>()
    private val keys = mutableMapOf<String, ByteArray>()

    override suspend fun create(code: String, inviterUid: String, coupleId: String) {
        docs[code] = inviterUid to coupleId
    }

    override suspend fun coupleId(code: String) = docs[code]?.second

    override suspend fun giverUid(code: String) = docs[code]?.first

    override suspend fun putStep(code: String, name: String, fields: Map<String, String>) {
        val s = steps.getOrPut(code) { mutableMapOf() }
        check(name !in s) { "단계는 한 번만 쓴다" }
        s[name] = fields
        val ci = s[Steps.CONFIRM_INVITER] ?: return
        val cj = s[Steps.CONFIRM_JOINER] ?: return
        if (ci["sas"] != cj["sas"]) return
        val c = docs.getValue(code).second
        val newDevice = ci.getValue("newDeviceId")
        active += "$c/$newDevice"
        keys["$c/$newDevice"] = ci.getValue("wrappedKey").unb64()
        // 함수(onRelinkStep)처럼 활성화하자마자 relinks/{code} 를 지운다.
        docs.remove(code)
        steps.remove(code)
    }

    override suspend fun getStep(code: String, name: String) = steps[code]?.get(name)

    override suspend fun activated(coupleId: String, deviceId: String) = "$coupleId/$deviceId" in active

    override suspend fun wrappedKeyFor(coupleId: String, deviceId: String) = keys["$coupleId/$deviceId"]
}
