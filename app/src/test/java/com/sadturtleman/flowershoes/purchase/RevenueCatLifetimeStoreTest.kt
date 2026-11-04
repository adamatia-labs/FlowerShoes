package com.sadturtleman.flowershoes.purchase

import com.sadturtleman.flowershoes.core.data.Identity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevenueCatLifetimeStoreTest {
    @Test
    fun `등록 즉시 구매 정보가 도착해도 두 상품 상태가 반영된다`() {
        val identity = object : Identity {
            override val userId = "user"
            override val deviceId = "device"
            override val keyEpoch = 1
        }
        lateinit var notify: (Set<String>) -> Unit
        val store = RevenueCatLifetimeStore(identity) { callback ->
            notify = callback
            callback(setOf("memory_book"))
        }

        assertFalse(store.owned.value)
        assertTrue(store.book.owned.value)

        notify(setOf("lifetime"))
        assertTrue(store.owned.value)
        assertFalse(store.book.owned.value)
    }
}
