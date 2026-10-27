package com.sadturtleman.flowershoes.core.firebase

import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.StorageReference
import dev.gitlive.firebase.storage.android
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun putStorageBytes(ref: StorageReference, bytes: ByteArray) { ref.putData(Data(bytes)) }

internal actual suspend fun getStorageBytes(ref: StorageReference): ByteArray = suspendCancellableCoroutine { continuation ->
    ref.android.getBytes(10L * 1024 * 1024)
        .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
}
