package com.sadturtleman.flowershoes.core.data.sync

/**
 * 테스트용 서버. Firestore · Storage · Cloud Function(onPairingStep) 이 하는 일을 메모리에서 흉내 낸다.
 * [tamper] 로 "서버가 암호문을 바꿔치기하는" 상황도 만든다.
 */
class InMemoryCloud : Mailbox, BlobStore, PairingTransport {
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
}
