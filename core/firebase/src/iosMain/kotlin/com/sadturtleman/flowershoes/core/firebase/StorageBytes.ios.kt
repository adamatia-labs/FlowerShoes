@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sadturtleman.flowershoes.core.firebase

import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.StorageReference
import dev.gitlive.firebase.storage.ios
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun putStorageBytes(ref: StorageReference, bytes: ByteArray) {
    val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    ref.putData(Data(data))
}

internal actual suspend fun getStorageBytes(ref: StorageReference): ByteArray = suspendCancellableCoroutine { continuation ->
    val task = ref.ios.dataWithMaxSize(10L * 1024 * 1024) { data, error ->
        if (continuation.isActive) {
            if (error != null || data == null) continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "첨부를 받지 못했어요"))
            else {
                val bytes = ByteArray(data.length.toInt())
                if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                continuation.resume(bytes)
            }
        }
    }
    continuation.invokeOnCancellation { task.cancel() }
}
