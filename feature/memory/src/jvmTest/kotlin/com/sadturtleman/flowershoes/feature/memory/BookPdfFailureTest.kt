package com.sadturtleman.flowershoes.feature.memory

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.data.BookStore
import com.sadturtleman.flowershoes.core.data.PurchaseOutcome
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BookPdfFailureTest {
    private fun ownedStore() = object : BookStore {
        override val owned = MutableStateFlow(true)
        override suspend fun price(): String? = null
        override suspend fun buy(): PurchaseOutcome = PurchaseOutcome.Cancelled
        override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Cancelled
    }

    @Test
    fun `한 쪽 생성이 실패하면 불완전한 PDF를 공유하지 않는다`() {
        startKoin {}
        try {
            runComposeUiTest {
                var shared = false
                var finished = false
                var aborted = false
                val notices = mutableListOf<String>()
                val store = ownedStore()
                val writer = object : BookPdf {
                    override fun add(page: ImageBitmap) { error("test page failure") }
                    override suspend fun finish(): List<Pair<String, ByteArray>> {
                        finished = true
                        return listOf("screen.pdf" to byteArrayOf(1), "print.pdf" to byteArrayOf(2))
                    }
                    override fun abort() { aborted = true }
                }
                val actions = MemoryBookActions(store, newPdf = { writer }, shareFiles = { shared = true })
                setContent { KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { BookScreen(MemoryState(loading = false), emptyMap(), actions, {}, {}, { notices += it }) } }
                onNodeWithText("PDF 받기").performScrollTo().performClick()
                waitUntil(timeoutMillis = 10_000) { shared || notices.isNotEmpty() }
                assertEquals(false, shared)
                assertEquals(false, finished)
                waitUntil(timeoutMillis = 5_000) { aborted }
                assertTrue(notices.any { it.contains("만들지 못했어요") })
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `PDF 두 파일 중 하나가 비면 공유하지 않는다`() {
        startKoin {}
        try {
            runComposeUiTest {
                var shared = false
                val notices = mutableListOf<String>()
                val writer = object : BookPdf {
                    override fun add(page: ImageBitmap) = Unit
                    override suspend fun finish() = listOf("screen.pdf" to byteArrayOf(1), "print.pdf" to byteArrayOf())
                    override fun abort() = Unit
                }
                val actions = MemoryBookActions(ownedStore(), newPdf = { writer }, shareFiles = { shared = true })
                setContent { KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { BookScreen(MemoryState(loading = false), emptyMap(), actions, {}, {}, { notices += it }) } }
                onNodeWithText("PDF 받기").performScrollTo().performClick()
                waitUntil(timeoutMillis = 10_000) { shared || notices.isNotEmpty() }
                assertEquals(false, shared)
                assertTrue(notices.any { it.contains("만들지 못했어요") })
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `생성 도중 화면 기록이 바뀌어도 시작한 책의 모든 쪽을 공유한다`() {
        startKoin {}
        try {
            runComposeUiTest {
                var shared: List<Pair<String, ByteArray>>? = null
                var added = 0
                val bookState = mutableStateOf(MemoryState(loading = false))
                val writer = object : BookPdf {
                    override fun add(page: ImageBitmap) {
                        added++
                        if (added == 1) {
                            val ym = YearMonth(2026, 1)
                            val month = MonthDetail(ym, 1, emptySet(), emptySet(), listOf(MonthBar(1, 1, "새 기록", BarKind.VISIT)), emptyList(), emptyList(), 0)
                            bookState.value = bookState.value.copy(months = mapOf(ym to month))
                        }
                    }
                    override suspend fun finish() = listOf("screen.pdf" to byteArrayOf(1), "print.pdf" to byteArrayOf(2))
                    override fun abort() = Unit
                }
                val actions = MemoryBookActions(ownedStore(), newPdf = { writer }, shareFiles = { shared = it })
                setContent { KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { BookScreen(bookState.value, emptyMap(), actions, {}, {}, {}) } }
                onNodeWithText("PDF 받기").performScrollTo().performClick()
                waitUntil(timeoutMillis = 10_000) { shared != null }
                assertEquals(3, added)
                assertEquals(2, shared?.size)
            }
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `만드는 중 화면을 떠나면 공유하지 않고 자원을 정리한다`() {
        startKoin {}
        try {
            runComposeUiTest {
                val visible = mutableStateOf(true)
                var aborted = false
                var shared = false
                val writer = object : BookPdf {
                    override fun add(page: ImageBitmap) { visible.value = false }
                    override suspend fun finish() = listOf("screen.pdf" to byteArrayOf(1), "print.pdf" to byteArrayOf(2))
                    override fun abort() { aborted = true }
                }
                val actions = MemoryBookActions(ownedStore(), newPdf = { writer }, shareFiles = { shared = true })
                setContent { KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { if (visible.value) BookScreen(MemoryState(loading = false), emptyMap(), actions, {}, {}, {}) } }
                onNodeWithText("PDF 받기").performScrollTo().performClick()
                waitUntil(timeoutMillis = 10_000) { aborted }
                assertEquals(false, shared)
            }
        } finally {
            stopKoin()
        }
    }
}
