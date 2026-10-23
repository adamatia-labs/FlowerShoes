package com.sadturtleman.flowershoes.core.compression

/** iOS: KkotsinApp.swift 가 시작할 때 `Zstd.shared.codec = SwiftZstd()`(libzstd, SPM)를 넣는다. */
internal actual fun platformZstd(): Compressor? = null
