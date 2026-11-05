package com.sadturtleman.flowershoes

import android.content.ContextWrapper
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.data.sync.AndroidKeyVault
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 모든 파일은 테스트 전용 디렉터리에서만 조작한다. 실제 꽃신 보관 파일은 사용하지 않는다. */
@RunWith(AndroidJUnit4::class)
class AndroidKeyVaultFailureTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(base.filesDir, "vault-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val isolated = object : ContextWrapper(base) { override fun getFilesDir(): File = root }
    private val file = File(root, "crypto/vault.bin")

    @After fun cleanup() { root.deleteRecursively() }

    @Test fun damagedVaultIsNotMistakenForAnEmptyVault() = runBlocking {
        file.parentFile!!.mkdirs()
        val damaged = byteArrayOf(1, 2, 3, 4)
        file.writeBytes(damaged)
        val failure = runCatching { AndroidKeyVault(isolated).deviceKeys() }.exceptionOrNull()
        assertNotNull("읽지 못한 기존 보관함을 빈 보관함으로 취급하면 안 된다", failure)
        assertNotNull("읽기 실패 뒤 새 키로 원본을 덮어쓰면 안 된다", runCatching {
            AndroidKeyVault(isolated).putCoupleKey(CoupleKey(1, ByteArray(32) { 7 }))
        }.exceptionOrNull())
        assertArrayEquals(damaged, file.readBytes())
    }

    @Test fun failedReplacementIsReportedInsteadOfPretendingToSave() = runBlocking {
        val vault = AndroidKeyVault(isolated)
        vault.currentCoupleKey() // 보관함이 아직 없을 때 초기 상태를 읽는다.
        file.mkdirs()
        val sentinel = File(file, "keep").apply { writeText("preserve") }
        val failure = runCatching { vault.putCoupleKey(CoupleKey(1, ByteArray(32) { 7 })) }.exceptionOrNull()
        assertNotNull("보관 파일 교체 실패를 성공으로 보고하면 안 된다", failure)
        assertTrue(sentinel.exists())
        assertTrue(file.deleteRecursively())
        vault.putCoupleKey(CoupleKey(1, ByteArray(32) { 7 }))
        assertArrayEquals(ByteArray(32) { 7 }, AndroidKeyVault(isolated).currentCoupleKey()!!.export())
    }

    @Test fun failedDeletionIsNotReportedAsAnEmptyVault() = runBlocking {
        val vault = AndroidKeyVault(isolated)
        vault.currentCoupleKey()
        file.mkdirs()
        val sentinel = File(file, "keep").apply { writeText("preserve") }
        val failure = runCatching { vault.clearAll() }.exceptionOrNull()
        assertNotNull("보관 파일 삭제 실패를 성공으로 보고하면 안 된다", failure)
        assertTrue(sentinel.exists())
    }

    @Test fun normalSaveReloadAndDeletionStillWork() = runBlocking {
        val key = ByteArray(32) { 9 }
        AndroidKeyVault(isolated).putCoupleKey(CoupleKey(1, key))
        val reopened = AndroidKeyVault(isolated)
        assertArrayEquals(key, reopened.currentCoupleKey()!!.export())
        reopened.clearAll()
        assertTrue(!file.exists())
        assertTrue(AndroidKeyVault(isolated).currentCoupleKey() == null)
    }

    @Test fun inaccessibleExistingVaultIsNotTreatedAsMissing() = runBlocking {
        val key = ByteArray(32) { 11 }
        AndroidKeyVault(isolated).putCoupleKey(CoupleKey(1, key))
        Os.chmod(file.parent!!, 0)
        try {
            val vault = AndroidKeyVault(isolated)
            assertNotNull("접근할 수 없는 보관함을 없는 파일로 취급하면 안 된다", runCatching { vault.currentCoupleKey() }.exceptionOrNull())
            assertNotNull("확인할 수 없는 보관함을 삭제 완료로 처리하면 안 된다", runCatching { vault.clearAll() }.exceptionOrNull())
        } finally { Os.chmod(file.parent!!, 448) }
        assertArrayEquals(key, AndroidKeyVault(isolated).currentCoupleKey()!!.export())
    }

    @Test fun deletionRemovesOrReportsTemporaryKeyMaterial() = runBlocking {
        val key = ByteArray(32) { 12 }
        val vault = AndroidKeyVault(isolated)
        vault.putCoupleKey(CoupleKey(1, key))
        val temporary = File(file.parentFile, "vault.bin.tmp")
        temporary.mkdirs()
        val sentinel = File(temporary, "keep").apply { writeText("preserve") }
        assertNotNull("임시 파일을 지울 수 없으면 전체 삭제에 성공했다고 보고하면 안 된다", runCatching { vault.clearAll() }.exceptionOrNull())
        assertTrue(sentinel.exists())
        assertArrayEquals(key, AndroidKeyVault(isolated).currentCoupleKey()!!.export())
        assertTrue(temporary.deleteRecursively())
        temporary.writeText("encrypted leftover")
        vault.clearAll()
        assertTrue(!file.exists() && !temporary.exists())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun cancellationAfterDeletionDoesNotRestoreOldCachedKey() = runTest {
        val vault = AndroidKeyVault(isolated)
        vault.putCoupleKey(CoupleKey(1, ByteArray(32) { 13 }))
        val deletion = launch { vault.clearAll() }
        runCurrent() // IO 작업이 끝나도 이 테스트 스레드의 재개는 runCurrent 전까지 보류한다.
        val deadline = System.nanoTime() + 5_000_000_000L
        while (file.exists() && System.nanoTime() < deadline) Thread.sleep(2)
        assertTrue("파일 삭제가 끝나야 취소 경계를 검증할 수 있다", !file.exists())
        deletion.cancel()
        runCurrent()
        assertNull("삭제된 키가 캐시에서 다시 나타나면 안 된다", vault.currentCoupleKey())
        vault.putCoupleKey(CoupleKey(2, ByteArray(32) { 14 }))
        val reopened = AndroidKeyVault(isolated)
        assertNull("새 키를 저장해도 삭제한 이전 키는 다시 나타나면 안 된다", reopened.coupleKey(1))
        assertArrayEquals(ByteArray(32) { 14 }, reopened.currentCoupleKey()!!.export())
    }
}
