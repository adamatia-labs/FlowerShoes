@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.core.crypto

import libsodium.sodium_init

internal actual fun initializeSodium() {
    // Native initialization is thread safe and waits for completion; 1 means already initialized.
    // Swift extensions can initialize the same library before Kotlin's wrapper runs.
    check(sodium_init() >= 0) { "암호화 라이브러리를 초기화할 수 없어요" }
}
