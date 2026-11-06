@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.core.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecNotAvailable
import platform.Security.errSecSuccess

class IosKeychainStoreTest {
    @Test
    fun `없는 항목은 추가한다`() {
        var adds = 0
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> errSecItemNotFound },
            addItem = { adds++; errSecSuccess },
        )
        store.write("test-item", byteArrayOf(1))
        assertEquals(1, adds)
    }

    @Test
    fun `항목 없음일 때만 추가하고 새 항목의 충돌은 다시 덮어쓰지 않는다`() {
        var updates = 0
        var adds = 0
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> updates++; errSecItemNotFound },
            addItem = { adds++; errSecDuplicateItem },
        )
        assertFailsWith<IllegalStateException> { store.write("test-item", byteArrayOf(1, 2, 3)) }
        assertEquals(1, updates)
        assertEquals(1, adds)
    }

    @Test
    fun `키체인 사용 불가 오류는 항목 없음으로 해석하거나 새로 추가하지 않는다`() {
        var adds = 0
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> errSecNotAvailable },
            addItem = { adds++; errSecSuccess },
        )
        val error = assertFailsWith<IllegalStateException> { store.write("test-item", byteArrayOf(1)) }
        assertTrue(error.message.orEmpty().contains(errSecNotAvailable.toString()))
        assertEquals(0, adds)
    }

    @Test
    fun `기존 항목 갱신 성공 뒤에는 추가하지 않는다`() {
        var adds = 0
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> errSecSuccess },
            addItem = { adds++; errSecSuccess },
        )
        store.write("test-item", byteArrayOf(1))
        assertEquals(0, adds)
    }

    @Test
    fun `새 항목 추가 오류를 성공으로 처리하지 않는다`() {
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> errSecItemNotFound },
            addItem = { errSecNotAvailable },
        )
        val error = assertFailsWith<IllegalStateException> { store.write("test-item", byteArrayOf(1)) }
        assertTrue(error.message.orEmpty().contains(errSecNotAvailable.toString()))
    }
}
