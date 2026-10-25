@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.core.firebase

import com.sadturtleman.flowershoes.core.backup.BackupStorage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.storage.ios
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.posix.memcpy
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses the configured Firebase app, including Auth/Storage emulators in local QA. */
class IosBackupStorage : BackupStorage {
    private val root get() = Firebase.storage.reference

    override suspend fun put(path: String, bytes: ByteArray) = putStorageBytes(root.child(path), bytes)

    override suspend fun get(path: String): ByteArray? = suspendCancellableCoroutine { continuation ->
        val task = root.child(path).ios.dataWithMaxSize(50L * 1024 * 1024) { data, error ->
            if (continuation.isActive) {
                when {
                    // FIRStorageErrorCodeObjectNotFound. Permission/network errors must propagate.
                    error?.code == -13010L -> continuation.resume(null)
                    error != null || data == null -> continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "백업을 받지 못했어요"))
                    else -> {
                        val bytes = ByteArray(data.length.toInt())
                        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                        continuation.resume(bytes)
                    }
                }
            }
        }
        continuation.invokeOnCancellation { task.cancel() }
    }

    override suspend fun list(prefix: String): List<BackupStorage.Entry> =
        root.child(prefix.trimEnd('/')).listAll().items.map { item ->
            val createdAt = suspendCancellableCoroutine<Long> { continuation ->
                item.ios.metadataWithCompletion { metadata, error ->
                    if (continuation.isActive) {
                        if (error != null) continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                        else continuation.resume(((metadata?.timeCreated()?.timeIntervalSince1970 ?: 0.0) * 1000).toLong())
                    }
                }
            }
            BackupStorage.Entry(item.path.trimStart('/'), createdAt)
        }

    override suspend fun delete(path: String) { root.child(path).delete() }
}
