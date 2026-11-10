package com.sadturtleman.flowershoes.core.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.app

internal actual fun createDeletionReceiptApp(): FirebaseApp {
    val original = com.google.firebase.FirebaseApp.getInstance()
    val context = original.applicationContext
    if (com.google.firebase.FirebaseApp.getApps(context).none { it.name == "deletion-receipt" }) {
        com.google.firebase.FirebaseApp.initializeApp(context, original.options, "deletion-receipt")
    }
    return Firebase.app("deletion-receipt")
}
