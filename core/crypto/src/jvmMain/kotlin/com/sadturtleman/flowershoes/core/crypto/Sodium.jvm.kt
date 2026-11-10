package com.sadturtleman.flowershoes.core.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer

@Synchronized
internal actual fun initializeSodium() {
    if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initializeWithCallback {}
}
