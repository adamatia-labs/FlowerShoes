@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.core.widget

import okio.FileSystem
import okio.Path
import platform.posix.*

internal actual fun <T> withSnapshotLock(file: Path, block: () -> T): T {
    val directory = checkNotNull(file.parent)
    FileSystem.SYSTEM.createDirectories(directory)
    // Swift SnapshotFileLock과 같은 고정 inode. 이 파일은 교체하거나 지우지 않는다.
    val fd = open((directory / ".snapshot.lock").toString(), O_RDWR or O_CREAT or O_CLOEXEC, 0x180)
    check(fd >= 0) { "위젯 저장 잠금 파일을 열 수 없어요" }
    try {
        while (flock(fd, LOCK_EX) != 0) {
            check(errno == EINTR) { "위젯 저장 잠금을 얻지 못했어요" }
        }
        try { return block() } finally { flock(fd, LOCK_UN) }
    } finally { close(fd) }
}
