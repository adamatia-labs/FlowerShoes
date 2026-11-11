package com.sadturtleman.flowershoes.core.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions

/** 만료된 기본 Auth 토큰을 결과 조회에 첨부하지 않는 별도 SDK 인스턴스. 로그인에는 쓰지 않는다. */
internal object DeletionReceiptFirebase {
    private val app by lazy { createDeletionReceiptApp() }

    fun functions(region: String, emulatorHost: String?): FirebaseFunctions = Firebase.functions(app, region).also {
        if (!emulatorHost.isNullOrBlank()) it.useEmulator(emulatorHost, 5001)
    }
}

internal expect fun createDeletionReceiptApp(): FirebaseApp
