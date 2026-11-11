package com.sadturtleman.flowershoes.core.firebase

import cocoapods.FirebaseCore.FIRApp
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.app
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
internal actual fun createDeletionReceiptApp(): FirebaseApp {
    if (FIRApp.appNamed("deletion-receipt") == null) {
        // GitLive's options conversion requires databaseURL and changes the application ID on iOS.
        // Keep the native options intact, including a Firestore-only configuration without databaseURL.
        FIRApp.configureWithName("deletion-receipt", options = checkNotNull(FIRApp.defaultApp()).options)
    }
    return Firebase.app("deletion-receipt")
}
