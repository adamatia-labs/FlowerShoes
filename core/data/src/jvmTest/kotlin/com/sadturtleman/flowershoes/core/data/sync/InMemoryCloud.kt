package com.sadturtleman.flowershoes.core.data.sync

/**
 * 테스트용 서버. Firestore · Storage · Cloud Function(onPairingStep) 이 하는 일을 메모리에서 흉내 낸다.
 * [tamper] 로 "서버가 암호문을 바꿔치기하는" 상황도 만든다.
 */
class InMemoryCloud : Mailbox, BlobStore, PairingTransport, RotationTransport, AccountTransport {
    /** 넘겨주기(relinks/…)는 초대(pairings/…)와 다른 컬렉션이다. */
    val relinks = RelinkCloud()

    private val inbox = mutableMapOf<String, MutableMap<String, MailItem>>() // "{c}/{device}" → opId → item
    private val control = mutableMapOf<String, ControlDoc>()
    private val blobStore = mutableMapOf<String, ByteArray>()
    private val invites = mutableMapOf<String, String>()
    private val steps = mutableMapOf<String, MutableMap<String, Map<String, String>>>()
    private val linked = mutableMapOf<String, String>()
    private val keys = mutableMapOf<String, ByteArray>()

    var tamper: ((MailItem) -> MailItem)? = null

    fun inboxSize(coupleId: String, device: String) = inbox["$coupleId/$device"]?.size ?: 0

    // Mailbox
    override suspend fun put(coupleId: String, toDevice: String, item: MailItem) {
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
        blobStore["$coupleId/$blobId"] = bytes
    }

    override suspend fun get(coupleId: String, blobId: String) = blobStore["$coupleId/$blobId"]

    override suspend fun delete(coupleId: String, blobId: String) {
        blobStore.remove("$coupleId/$blobId")
    }

    fun blobCount() = blobStore.size

    // PairingTransport
    override suspend fun createInvite(code: String, inviterUid: String) {
        invites[code] = inviterUid
    }

    override suspend fun inviterUid(code: String) = invites[code]

    override suspend fun putStep(code: String, name: String, fields: Map<String, String>) {
        val s = steps.getOrPut(code) { mutableMapOf() }
        check(name !in s) { "단계는 한 번만 쓴다(보안 규칙과 같게)" }
        s[name] = fields
        onPairingStep(code)
    }

    override suspend fun getStep(code: String, name: String) = steps[code]?.get(name)

    override suspend fun linkedCoupleId(code: String) = linked[code]

    override suspend fun wrappedKeyFor(coupleId: String, deviceId: String) = keys["$coupleId/$deviceId"]

    /** Cloud Function onPairingStep 과 같은 규칙: 두 확인이 모두 있고 SAS 가 같으면 커플을 만든다. */
    private fun onPairingStep(code: String) {
        val s = steps[code] ?: return
        val ci = s[Steps.CONFIRM_INVITER] ?: return
        val cj = s[Steps.CONFIRM_JOINER] ?: return
        if (ci["sas"] != cj["sas"]) {
            steps.remove(code)
            return
        }
        val coupleId = "couple-$code"
        val joinerDevice = s.getValue(Steps.REVEAL).getValue("deviceId")
        keys["$coupleId/$joinerDevice"] = ci.getValue("wrappedKey").unb64()
        linked[code] = coupleId
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
        inbox.keys.removeAll { it.startsWith("$coupleId/") }
        control.keys.removeAll { it.startsWith("$coupleId/") }
        blobStore.keys.removeAll { it.startsWith("$coupleId/") }
    }

    override suspend fun deleteAccount() = Unit
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
    }

    override suspend fun getStep(code: String, name: String) = steps[code]?.get(name)

    override suspend fun activated(coupleId: String, deviceId: String) = "$coupleId/$deviceId" in active

    override suspend fun wrappedKeyFor(coupleId: String, deviceId: String) = keys["$coupleId/$deviceId"]
}
