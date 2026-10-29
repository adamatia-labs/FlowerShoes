package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Role
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 조합 컴포넌트(Figma 341:82 · 340:89) 견본을 테마 v2 4개 모드로 build/render 에 그린다.
 * Figma 와 눈으로 맞춰 보는 용도(PR 스크린샷).
 */
class PaperComponentsRenderTest {

    @Test
    fun `조합 컴포넌트를 4개 모드로 그린다`() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val png = ImageComposeScene(width = 390 * 2, height = 1080 * 2, density = Density(2f)) {
                KkotsinTheme(theme = theme, dark = dark) {
                    CompositionLocalProvider(LocalAds provides { KkAdContent("광고 제목", "광고 내용 한 줄") }) { Sheet() }
                }
            }.use { scene ->
                scene.render() // 첫 프레임에 글꼴을 불러온다
                scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            }
            val name = "paper_components_${theme.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            val out = File("build/render/$name").apply { parentFile.mkdirs(); writeBytes(png) }
            assertTrue(out.length() > 0)
        }
    }
}

@Composable
private fun Sheet() {
    val c = KkTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.canvas).padding(horizontal = KkSpacing.screen, vertical = KkSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(KkSpacing.lg),
    ) {
        KkUnderlineTabs(listOf("전체", "받은", "보낸"), selected = 0, onSelect = {})
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            KkPaperTag("다음 휴가", "D-23", modifier = Modifier.padding(top = KkSpacing.sm))
            KkPaperTag("다음 만남", "11월 2일", valueStyle = KkPaperTagValue.Hand, tilt = -2f, modifier = Modifier.padding(top = KkSpacing.sm))
        }
        KkEnvelopeRow("오늘도 보고 싶어. 휴가 나오면 그 카페 가자.", "민준 · 10월 28일", KkEnvelopeMark.Seal("D-479"), onClick = {})
        KkEnvelopeRow("그림 보냈어, 맞혀 봐", "민준 · 10월 27일", KkEnvelopeMark.Polaroid(null), onClick = {})
        KkEnvelopeRow("훈련 잘 다녀와", "나 · 10월 26일", KkEnvelopeMark.Postmark("2026.10.26"), onClick = {})
        KkEnvelopeRow("오늘 점심은 돈가스였어", "민준 · 10월 25일", KkEnvelopeMark.Stamp("D-482"), onClick = {})
        KkServiceProgress(0.423f, "입대 2026.04.20", "전역 2028.02.19", "42.3%")
        KkServiceProgress(0.423f, "입대 2026.04.20", "전역 2028.02.19", "42.3%", style = KkServiceProgressStyle.Months, months = 21)
        Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.xl), verticalAlignment = Alignment.Top) {
            KkPolaroid(Modifier.width(110.dp).padding(top = KkSpacing.sm), tilt = 3f, caption = "첫 면회", onReplay = {}) {
                Box(Modifier.fillMaxSize().background(Palette.InkBrown100))
            }
            Column(verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                    KkPostmark(KkPostmarkInk.of(Role.SOLDIER), "2026.10.28")
                    KkPostmark(KkPostmarkInk.of(Role.GOMSIN), "2026.10.28")
                    KkPostmark("보냄", "2026.10.28")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    KkMissYouFab(sent = false, onClick = {})
                    KkMissYouFab(sent = true, onClick = {})
                }
                KkSkeleton(Modifier.width(200.dp))
                KkSkeleton(Modifier.width(140.dp))
                KkTape(Modifier.size(56.dp, 16.dp))
            }
        }
        KkAd(KkAdSlot.SETTINGS, Modifier.fillMaxWidth())
    }
}
