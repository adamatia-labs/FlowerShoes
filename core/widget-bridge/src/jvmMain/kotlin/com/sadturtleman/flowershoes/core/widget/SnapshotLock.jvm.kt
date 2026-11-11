package com.sadturtleman.flowershoes.core.widget

import okio.Path

internal actual fun <T> withSnapshotLock(file: Path, block: () -> T): T = block()
