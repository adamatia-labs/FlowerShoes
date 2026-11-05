package com.sadturtleman.flowershoes

import android.app.Service
import android.content.ContextWrapper
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.data.sync.AndroidKeyVault
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking

/** 디버그 계측 시험 전용. 임의의 사용자 경로를 받지 않는다. */
class VaultLockTestService : Service() {
    override fun onBind(intent: Intent): IBinder {
        val name = requireNotNull(intent.getStringExtra("testDirectory"))
        require(Regex("vault-concurrency-[0-9a-f-]{36}").matches(name))
        val root = File(filesDir, name)
        Thread {
            if (intent.getStringExtra("mode") == "write") {
                val isolated = object : ContextWrapper(this) { override fun getFilesDir(): File = root }
                runBlocking { AndroidKeyVault(isolated).putCoupleKey(CoupleKey(1, ByteArray(32) { 3 })) }
                File(root, "written").writeText("${Process.myPid()}:done")
                return@Thread
            }
            val directory = File(root, "crypto").apply { mkdirs() }
            RandomAccessFile(File(directory, "vault.lock"), "rw").use { lockFile ->
                lockFile.channel.use { channel ->
                    channel.lock().use {
                        File(root, "locked").writeText(Process.myPid().toString())
                        val deadline = System.nanoTime() + 15_000_000_000L
                        while (!File(root, "release").exists() && System.nanoTime() < deadline) Thread.sleep(10)
                    }
                }
            }
            File(root, "released").writeText("done")
        }.start()
        return Binder()
    }
}
