package com.sadturtleman.flowershoes.core.firebase

internal actual fun firebaseApplicationContext(): Any? = com.google.firebase.FirebaseApp.getInstance().applicationContext
