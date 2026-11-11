package com.sadturtleman.flowershoes.feature.memory

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.data.BookStore
import com.sadturtleman.flowershoes.core.data.PurchaseOutcome
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.BookCover
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BookCoverPermissionTest {
    private class Store(purchased: Boolean) : BookStore {
        override val owned = MutableStateFlow(purchased)
        var purchases = 0
        override suspend fun price(): String? = null
        override suspend fun buy(): PurchaseOutcome {
            purchases++
            owned.value = true
            return PurchaseOutcome.Purchased
        }
        override suspend fun restore() = PurchaseOutcome.Cancelled
    }

    @Test
    fun `상대가 산 책은 PDF를 받되 표지는 바꿀 수 없다`() {
        startKoin {}
        try {
            runComposeUiTest {
                val store = Store(false)
                var exports = 0
                val chosen = mutableListOf<BookCover>()
                val actions = MemoryBookActions(store, { exports++; null }, {}, MutableStateFlow(true))
                setContent { KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { BookScreen(MemoryState(loading = false), emptyMap(), actions, chosen::add, {}, {}) } }
                onNodeWithText("PDF 받기").performScrollTo().performClick()
                onNodeWithText("표지 바꾸기").assertDoesNotExist()
                runOnIdle {
                    assertEquals(1, exports)
                    assertEquals(0, store.purchases)
                    assertEquals(emptyList(), chosen)
                }
            }
        } finally { stopKoin() }
    }

    @Test
    fun `구매자는 공유 표지를 바꾸고 PDF를 다시 만든다`() = selectCover(purchased = true)

    @Test
    fun `처음 구매할 때 고른 표지는 결제 성공 뒤 반영한다`() = selectCover(purchased = false)

    private fun selectCover(purchased: Boolean) {
        startKoin {}
        try {
            runComposeUiTest {
                val store = Store(purchased)
                var exports = 0
                val chosen = mutableListOf<BookCover>()
                val actions = MemoryBookActions(store, { exports++; null }, {}, store.owned)
                setContent { KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { BookScreen(MemoryState(loading = false), emptyMap(), actions, chosen::add, {}, {}) } }
                onNodeWithText(if (purchased) "표지 바꾸기" else "5,900원에 PDF 받기").performScrollTo().performClick()
                onNodeWithText("꽃신 편지지").performClick()
                runOnIdle { assertEquals(emptyList(), chosen) }
                onNodeWithText("이 표지로 만들기").performClick()
                waitUntil { exports == 1 }
                runOnIdle {
                    assertEquals(listOf(BookCover.FLOWER), chosen)
                    assertEquals(if (purchased) 0 else 1, store.purchases)
                }
            }
        } finally { stopKoin() }
    }

    @Test
    fun `표지 선택 중 본인 구매 권한이 없어지면 상대의 표지를 바꾸지 않는다`() {
        startKoin {}
        try {
            runComposeUiTest {
                val store = Store(true)
                var exports = 0
                val chosen = mutableListOf<BookCover>()
                val actions = MemoryBookActions(store, { exports++; null }, {}, MutableStateFlow(true))
                setContent { KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { BookScreen(MemoryState(loading = false), emptyMap(), actions, chosen::add, {}, {}) } }
                onNodeWithText("표지 바꾸기").performScrollTo().performClick()
                onNodeWithText("꽃신 편지지").performClick()
                runOnIdle { store.owned.value = false }
                onNodeWithText("이 표지로 만들기").performClick()
                runOnIdle {
                    assertEquals(emptyList(), chosen)
                    assertEquals(0, store.purchases)
                }
            }
        } finally { stopKoin() }
    }
}

