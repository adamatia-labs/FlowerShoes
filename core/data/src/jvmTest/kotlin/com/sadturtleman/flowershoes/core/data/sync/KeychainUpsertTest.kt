package com.sadturtleman.flowershoes.core.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeychainUpsertTest {
    private class Item(var value: String? = null) {
        var failUpdate = false
        var failAdd = false
        var concurrentInsert: String? = null

        fun update(value: String): Boolean {
            if (failUpdate) error("update failed")
            if (this.value == null) return false
            this.value = value
            return true
        }

        fun add(value: String): Boolean {
            if (failAdd) error("add failed")
            concurrentInsert?.let { this.value = it; concurrentInsert = null; return false }
            if (this.value != null) return false
            this.value = value
            return true
        }

        fun save(value: String) = upsertKeychainItem({ update(value) }, { add(value) })
    }

    @Test fun `기존 항목을 갱신하지 못하면 이전 값은 남는다`() {
        val item = Item("기존 키").apply { failUpdate = true }
        assertFailsWith<IllegalStateException> { item.save("새 키") }
        assertEquals("기존 키", item.value)
    }

    @Test fun `기존 항목을 갱신하고 없는 항목은 추가한다`() {
        val item = Item("기존 키")
        item.save("갱신한 키")
        assertEquals("갱신한 키", item.value)
        val empty = Item()
        empty.save("첫 키")
        assertEquals("첫 키", empty.value)
    }

    @Test fun `추가가 실패하면 성공한 것으로 처리하지 않는다`() {
        val item = Item().apply { failAdd = true }
        assertFailsWith<IllegalStateException> { item.save("새 키") }
        assertEquals(null, item.value)
    }

    @Test fun `항목 없음 확인과 추가 사이 다른 작업이 만들면 그 값을 보존한다`() {
        val item = Item().apply { concurrentInsert = "동시에 만든 키" }
        assertFailsWith<IllegalStateException> { item.save("요청한 키") }
        assertEquals("동시에 만든 키", item.value)
    }

    @Test fun `동시 추가 뒤에는 업데이트를 다시 시도하지 않는다`() {
        val item = Item()
        var updateCalls = 0
        assertFailsWith<IllegalStateException> {
            upsertKeychainItem(
                update = { updateCalls++; false },
                add = { item.value = "동시에 만든 키"; false },
            )
        }
        assertEquals("동시에 만든 키", item.value)
        assertEquals(1, updateCalls)
    }
}
