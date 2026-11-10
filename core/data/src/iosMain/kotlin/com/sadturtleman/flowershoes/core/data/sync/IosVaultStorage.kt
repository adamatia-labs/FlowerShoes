@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.core.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.posix.*

/** The stable lock inode is shared by every process with access to this App Group. */
internal class IosVaultStorage(
    private val items: VaultItems,
    private val directory: String = defaultDirectory(),
) : VaultStorage {
    override suspend fun <T> transaction(block: suspend VaultItems.() -> T): T = withContext(Dispatchers.Default) {
        check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)) { "키 보관 잠금 폴더를 열 수 없어요" }
        val fd = open("$directory/vault.lock", O_RDWR or O_CREAT or O_CLOEXEC, 0x180 /* 0600 */)
        check(fd >= 0) { "키 보관 잠금 파일을 열 수 없어요" }
        try {
            while (flock(fd, LOCK_EX or LOCK_NB) != 0) {
                check(errno == EWOULDBLOCK || errno == EINTR) { "키 보관 잠금을 얻지 못했어요" }
                delay(25)
            }
            try { items.block() } finally { flock(fd, LOCK_UN) }
        } finally { close(fd) }
    }

    companion object {
        private fun defaultDirectory(): String {
            val manager = NSFileManager.defaultManager
            val root = manager.containerURLForSecurityApplicationGroupIdentifier("group.com.sadturtleman.kkotsin")?.path
                ?: manager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)?.path
            return checkNotNull(root) { "키 보관 잠금 위치를 확인할 수 없어요" } + "/crypto-lock"
        }
    }
}
