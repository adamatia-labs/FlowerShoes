package com.sadturtleman.flowershoes

import android.content.ContextWrapper
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.data.sync.AndroidKeyVault
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 보관 파일과 분리된 시험 전용 디렉터리만 사용한다. */
@RunWith(AndroidJUnit4::class)
class AndroidKeyVaultConcurrencyTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(base.filesDir, "vault-concurrency-${UUID.randomUUID()}").apply { mkdirs() }
    private val isolated = object : ContextWrapper(base) { override fun getFilesDir(): File = root }

    @After fun cleanup() { root.deleteRecursively() }

    private fun connection() = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    private suspend fun withHeldRemoteLock(block: suspend () -> Unit) {
        val connection = connection()
        assertTrue(base.bindService(Intent(base, VaultLockTestService::class.java).putExtra("testDirectory", root.name), connection, Context.BIND_AUTO_CREATE))
        try {
            val marker = File(root, "locked")
            withTimeout(5_000) { while (!marker.exists() || marker.readText().toIntOrNull() == null) delay(10) }
            assertTrue(marker.readText().toInt() != Process.myPid())
            block()
        } finally {
            File(root, "release").writeText("release")
            withTimeout(5_000) { while (!File(root, "released").exists()) delay(10) }
            base.unbindService(connection)
        }
    }

    @Test fun staleSecondInstanceCannotOverwriteAnotherEpoch() = runBlocking {
        val first = AndroidKeyVault(isolated)
        val second = AndroidKeyVault(isolated)
        assertNull(first.currentCoupleKey())
        assertNull(second.currentCoupleKey())
        first.putCoupleKey(CoupleKey(1, ByteArray(32) { 3 }))
        second.putCoupleKey(CoupleKey(2, ByteArray(32) { 4 }))
        val reopened = AndroidKeyVault(isolated)
        assertNotNull("다른 인스턴스가 저장한 기존 키를 지우면 안 된다", reopened.coupleKey(1))
        assertArrayEquals(ByteArray(32) { 3 }, reopened.coupleKey(1)!!.export())
        assertArrayEquals(ByteArray(32) { 4 }, reopened.coupleKey(2)!!.export())
    }

    @Test fun staleInstanceCannotRestoreKeysAfterClearAll() = runBlocking {
        val first = AndroidKeyVault(isolated)
        val second = AndroidKeyVault(isolated)
        first.putCoupleKey(CoupleKey(1, ByteArray(32) { 3 }))
        assertArrayEquals(ByteArray(32) { 3 }, second.currentCoupleKey()!!.export())
        first.clearAll()
        second.putCoupleKey(CoupleKey(2, ByteArray(32) { 4 }))
        val reopened = AndroidKeyVault(isolated)
        assertNull("삭제된 이전 키가 다른 인스턴스의 캐시에서 복원되면 안 된다", reopened.coupleKey(1))
        assertArrayEquals(ByteArray(32) { 4 }, reopened.currentCoupleKey()!!.export())
    }

    @Test fun staleInstancePreservesAnotherProcessWrite() = runBlocking {
        val stale = AndroidKeyVault(isolated)
        assertNull(stale.currentCoupleKey())
        val connection = connection()
        val intent = Intent(base, VaultLockTestService::class.java).putExtra("testDirectory", root.name).putExtra("mode", "write")
        assertTrue(base.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        try {
            val marker = File(root, "written")
            withTimeout(5_000) { while (!marker.exists() || !marker.readText().endsWith(":done")) delay(10) }
            assertTrue(marker.readText().substringBefore(':').toInt() != Process.myPid())
            stale.putCoupleKey(CoupleKey(2, ByteArray(32) { 4 }))
            val reopened = AndroidKeyVault(isolated)
            assertNotNull("다른 프로세스가 저장한 키를 지우면 안 된다", reopened.coupleKey(1))
            assertArrayEquals(ByteArray(32) { 4 }, reopened.coupleKey(2)!!.export())
        } finally { base.unbindService(connection) }
    }

    @Test fun waitingForAnotherProcessCanBeCancelled() = runBlocking {
        withHeldRemoteLock {
            val attempt = async(Dispatchers.Default) { AndroidKeyVault(isolated).currentCoupleKey() }
            try {
                delay(100)
                assertFalse(attempt.isCompleted)
                attempt.cancel()
                withTimeout(1_000) { attempt.join() }
            } finally { attempt.cancel() }
        }
    }

    @Test fun waitingForOneVaultDoesNotBlockAnotherDirectory() = runBlocking {
        val otherRoot = File(base.filesDir, "vault-concurrency-${UUID.randomUUID()}").apply { mkdirs() }
        val other = object : ContextWrapper(base) { override fun getFilesDir(): File = otherRoot }
        try {
            withHeldRemoteLock {
                val attempt = async(Dispatchers.Default) { AndroidKeyVault(isolated).currentCoupleKey() }
                try {
                    delay(100)
                    assertFalse(attempt.isCompleted)
                    withTimeout(1_000) { assertNull(AndroidKeyVault(other).currentCoupleKey()) }
                } finally { attempt.cancel() }
            }
        } finally { otherRoot.deleteRecursively() }
    }

    @Test fun operatingSystemReleasesLockWhenTestProcessDies() = runBlocking {
        val connection = connection()
        assertTrue(base.bindService(Intent(base, VaultLockTestService::class.java).putExtra("testDirectory", root.name), connection, Context.BIND_AUTO_CREATE))
        try {
            val marker = File(root, "locked")
            withTimeout(5_000) { while (!marker.exists() || marker.readText().toIntOrNull() == null) delay(10) }
            val childPid = marker.readText().toInt()
            assertTrue(childPid != Process.myPid())
            Process.killProcess(childPid)
            withTimeout(5_000) { AndroidKeyVault(isolated).putCoupleKey(CoupleKey(1, ByteArray(32) { 3 })) }
            assertArrayEquals(ByteArray(32) { 3 }, AndroidKeyVault(isolated).currentCoupleKey()!!.export())
        } finally { base.unbindService(connection) }
    }
}
