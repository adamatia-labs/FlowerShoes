package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Pairing

/**
 * 테스트용 서버. Firestore · Storage · Cloud Function(onInviteAccepted 등) 이 하는 일을 메모리에서 흉내 낸다.
 * [tamper] 로 "서버가 암호문을 바꿔치기하는" 상황도 만든다.
 */
class InMemoryCloud : Mailbox, BlobStore, PairingTransport, AccountTransport {
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

    // 기기 교체 — 함수 onDeviceChangeCreated · onDeviceChangeAnswered · finalizeDevice 와 같은 확인을 한다.
    private val pendingDevices = mutableMapOf<String, Pair<String, Pairing.PublicKeys>>() // deviceId → (uid, keys)
    private val changeDocs = mutableMapOf<String, MutableList<DeviceChangeRequest>>()
    private val keyEnvelopes = mutableMapOf<String, ByteArray>() // "{c}/{device}" → 봉투

    /** 함수가 보낸 푸시(uid, 문구). 데이터만 보내는 푸시는 문구가 null. */
    val pushes = mutableListOf<Pair<String, String?>>()

    /** 지금 시각(요청 만료 · 24시간 판단). 테스트가 앞당긴다. */
    var nowMs: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() }

    /** 답을 처리하는 함수(onDeviceChangeAnswered)를 잠시 멈춘다 — 옛 폰의 막기와 상대의 승인이 겨루는 상황. */
    var holdFunctions = false
    private val heldFunctions = mutableListOf<() -> Unit>()

    fun releaseFunctions() {
        holdFunctions = false
        heldFunctions.toList().also { heldFunctions.clear() }.forEach { it() }
    }

    private fun function(run: () -> Unit) {
        if (holdFunctions) heldFunctions += run else run()
    }

    /** 로그인한 사람마다 다른 창구(규칙 · 함수는 호출한 uid 를 안다). */
    fun deviceChange(uid: String): DeviceChangeTransport = DeviceChangeCloud(uid)

    fun serverCoupleIdOf(uid: String): String? = coupleDevices.entries.firstOrNull { e -> e.value.any { it.uid == uid } }?.key

    fun changeRequests(coupleId: String): List<DeviceChangeRequest> = changeDocs[coupleId].orEmpty()

    private fun update(coupleId: String, id: String, f: (DeviceChangeRequest) -> DeviceChangeRequest) {
        val list = changeDocs.getValue(coupleId)
        val i = list.indexOfFirst { it.id == id }
        list[i] = f(list[i])
    }

    private inner class DeviceChangeCloud(private val uid: String) : DeviceChangeTransport {
        private fun members(c: String) = coupleDevices[c].orEmpty()

        private fun request(c: String, id: String) = checkNotNull(changeDocs[c]?.firstOrNull { it.id == id }) { "요청 없음" }

        override suspend fun serverCoupleId() = coupleDevices.entries.firstOrNull { e -> e.value.any { it.uid == uid } }?.key

        override suspend fun currentDevice(coupleId: String, uid: String) = members(coupleId).firstOrNull { it.uid == uid }?.deviceId

        override suspend fun request(coupleId: String, request: DeviceChangeRequest, keys: Pairing.PublicKeys) {
            check(members(coupleId).any { it.uid == uid } && request.uid == uid) { "규칙: 커플 멤버가 자기 요청만" }
            check(request.oldDeviceId == currentDevice(coupleId, uid)) { "규칙: oldDeviceId = members[uid]" }
            pendingDevices.getOrPut(request.newDeviceId) { uid to keys }
            val list = changeDocs.getOrPut(coupleId) { mutableListOf() }
            val now = nowMs()
            // onDeviceChangeCreated: 24시간 안 두 번째 요청 표시, 같은 사람의 이전 대기 요청은 취소.
            val second = list.any { it.uid == uid && now - it.createdAtMs < 24 * 3600_000L }
            list.replaceAll { if (it.uid == uid && it.status == DeviceChangeStatus.PENDING) it.copy(status = DeviceChangeStatus.CANCELLED) else it }
            list += request.copy(createdAtMs = now, second = second)
            val partner = members(coupleId).first { it.uid != uid }.uid
            pushes += partner to "새 폰으로 바꿨대요. 맞나요?"
            pushes += uid to "새 폰에서 로그인했어요"
        }

        override suspend fun requests(coupleId: String): List<DeviceChangeRequest> {
            check(members(coupleId).any { it.uid == uid }) { "규칙: 멤버만 읽는다" }
            return changeDocs[coupleId].orEmpty().toList()
        }

        override suspend fun deviceKeys(coupleId: String, deviceId: String) =
            pendingDevices[deviceId]?.second ?: members(coupleId).firstOrNull { it.deviceId == deviceId }?.keys

        override suspend fun approve(coupleId: String, requestId: String, approval: DeviceChangeApproval) {
            val r = request(coupleId, requestId)
            val approver = members(coupleId).first { it.uid == uid }
            check(r.uid != uid && approver.deviceId == approval.approverDeviceId) { "규칙: 상대 활성 기기만 승인" }
            check(r.status == DeviceChangeStatus.PENDING && r.expireAtMs > nowMs()) { "규칙: 대기 중인 요청만" }
            keyEnvelopes["$coupleId/${r.newDeviceId}"] = approval.wrappedKey
            // 함수: 아직 대기 중인지 다시 보고 · 승인 서명 확인 → 새 기기 활성화 · 옛 기기 폐기 · members 갱신.
            function {
                if (request(coupleId, requestId).status != DeviceChangeStatus.PENDING) return@function
                val ok = com.sadturtleman.flowershoes.core.crypto.Signatures.verify(
                    DeviceChangeMessages.approval(coupleId, requestId, r.newDeviceId, approval.cutoff), approval.sig, approver.keys.signPub,
                )
                if (!ok) return@function
                val (_, keys) = pendingDevices.remove(r.newDeviceId) ?: return@function
                coupleDevices[coupleId] = members(coupleId).map { if (it.uid == r.uid) LinkedDevice(r.uid, r.newDeviceId, keys) else it }
                update(coupleId, requestId) { it.copy(status = DeviceChangeStatus.APPROVED, answeredAtMs = nowMs(), counts = approval.counts) }
                pushes += r.uid to null
            }
        }

        override suspend fun reject(coupleId: String, requestId: String) {
            val r = request(coupleId, requestId)
            check(r.uid != uid && r.status == DeviceChangeStatus.PENDING) { "규칙: 상대의 대기 요청만" }
            function {
                if (request(coupleId, requestId).status != DeviceChangeStatus.PENDING) return@function
                pendingDevices.remove(r.newDeviceId)
                update(coupleId, requestId) { it.copy(status = DeviceChangeStatus.REJECTED, answeredAtMs = nowMs()) }
                pushes += r.uid to null
            }
        }

        override suspend fun cancel(coupleId: String, requestId: String, block: Boolean) {
            val r = request(coupleId, requestId)
            check(r.uid == uid && r.status == DeviceChangeStatus.PENDING) { "규칙: 내 대기 요청만" }
            function {
                if (request(coupleId, requestId).status != DeviceChangeStatus.PENDING) return@function
                pendingDevices.remove(r.newDeviceId)
                update(coupleId, requestId) { it.copy(status = if (block) DeviceChangeStatus.BLOCKED else DeviceChangeStatus.CANCELLED, answeredAtMs = nowMs()) }
                val partner = members(coupleId).first { it.uid != uid }.uid
                pushes += partner to if (block) "새 폰 요청을 막았어요" else null
            }
        }

        override suspend fun wrappedKey(coupleId: String, deviceId: String): ByteArray? {
            // 규칙: keys/{d} 는 members[uid] == d 일 때만 읽는다(활성화 뒤).
            if (currentDevice(coupleId, uid) != deviceId) return null
            return keyEnvelopes["$coupleId/$deviceId"]
        }

        override suspend fun partnerDevice(coupleId: String, myUid: String) = members(coupleId).firstOrNull { it.uid != myUid }

        override suspend fun finalize(coupleId: String, requestId: String, challengeSig: ByteArray): String {
            val r = request(coupleId, requestId)
            check(r.uid == uid) { "내 요청이 아니다" }
            val device = checkNotNull(members(coupleId).firstOrNull { it.deviceId == r.newDeviceId }) { "상대 폰이 아직 확인하지 않았다" }
            check(com.sadturtleman.flowershoes.core.crypto.Signatures.verify(DeviceChangeMessages.finalize(coupleId, requestId, uid), challengeSig, device.keys.signPub)) { "새 기기 서명이 맞지 않는다" }
            return "custom-token-for-$uid"
        }
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
