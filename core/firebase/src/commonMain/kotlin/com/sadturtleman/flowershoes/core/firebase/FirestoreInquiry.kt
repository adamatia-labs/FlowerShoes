package com.sadturtleman.flowershoes.core.firebase

import com.sadturtleman.flowershoes.core.data.Inquiry
import com.sadturtleman.flowershoes.core.data.InquiryTransport
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
private data class InquiryDoc(
    val type: String,
    val body: String,
    val email: String,
    val device: String?,
    val shots: Int,
    val status: String = "pending",
    val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
)

@Serializable
private data class ShotDoc(val data: String)

/** 문의(users/{uid}/inquiries/{id}, 스크린샷은 그 아래 shots/{0..2}). 답변은 관리자가 answer · answeredAt · title 을 쓴다. */
class FirestoreInquiry(private val db: FirebaseFirestore = Firebase.firestore) : InquiryTransport {

    private fun mine(uid: String) = db.collection("users").document(uid).collection("inquiries")

    override suspend fun submit(type: String, body: String, email: String, device: String?, screenshots: List<ByteArray>) {
        val uid = checkNotNull(Firebase.auth.currentUser?.uid) { "로그인 전" }
        val shots = screenshots.take(3).map { Base64.encode(it) }.filter { it.length <= MAX_SHOT_B64 }
        val ref = mine(uid).document
        ref.set(InquiryDoc.serializer(), InquiryDoc(type, body, email, device, shots.size))
        shots.forEachIndexed { i, b64 -> ref.collection("shots").document("$i").set(ShotDoc.serializer(), ShotDoc(b64)) }
    }

    override fun observeMine(): Flow<List<Inquiry>> {
        val uid = Firebase.auth.currentUser?.uid ?: return emptyFlow()
        return mine(uid).orderBy("createdAt", Direction.DESCENDING).snapshots.map { snap ->
            snap.documents.map { d ->
                val body = d.get<String?>("body").orEmpty()
                Inquiry(
                    id = d.id,
                    type = d.get<String?>("type").orEmpty(),
                    // 답변하면서 관리자가 다듬은 제목이 있으면 그것, 아니면 본문 첫 줄.
                    title = d.get<String?>("title")?.takeIf { it.isNotBlank() } ?: body.lineSequence().first().take(120),
                    createdAtMs = runCatching { d.get<Timestamp?>("createdAt")?.ms() }.getOrNull() ?: 0L,
                    answer = d.get<String?>("answer")?.takeIf { it.isNotBlank() },
                    answeredAtMs = runCatching { d.get<Timestamp?>("answeredAt")?.ms() }.getOrNull(),
                )
            }
        }
    }

    override fun accountEmail(): String? = Firebase.auth.currentUser?.email?.takeIf { it.isNotBlank() }

    private fun Timestamp.ms(): Long = seconds * 1000 + nanoseconds / 1_000_000

    private companion object {
        /** 문서 1MB 한도 안(규칙은 950,000자). */
        const val MAX_SHOT_B64 = 940_000
    }
}
