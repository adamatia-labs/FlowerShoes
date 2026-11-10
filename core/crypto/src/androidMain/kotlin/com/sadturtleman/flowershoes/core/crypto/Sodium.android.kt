package com.sadturtleman.flowershoes.core.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer

// The binding's check and load/initialize are not atomic; app startup and workers can race.
@Synchronized
internal actual fun initializeSodium() {
    if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initializeWithCallback {}
}
