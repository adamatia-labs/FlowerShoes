package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.model.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 테마 v2 4개 모드(곰신 · 군인 × 라이트 · 다크)의 견본: 색 토큰 · 텍스트 스타일 · 지금 컴포넌트를 build/render 에 남긴다.
 * Figma 와 눈으로 맞춰 보는 용도(PR 스크린샷).
 */
class ThemeV2RenderTest {

    @Test
    fun `4개 모드 견본을 그린다`() {
        for (theme in AppTheme.entries) for (dark in listOf(false, true)) {
            val png = ImageComposeScene(width = 390 * 2, height = 844 * 2, density = Density(2f)) {
                KkotsinTheme(theme = theme, dark = dark) { Sheet() }
            }.use { scene ->
                scene.render() // 첫 프레임에 글꼴을 불러온다
                scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            }
            val name = "theme_v2_${theme.name.lowercase()}_${if (dark) "dark" else "light"}.png"
            val out = File("build/render/$name").apply { parentFile.mkdirs(); writeBytes(png) }
            assertTrue(out.length() > 0)
        }
    }
}

@Composable
private fun Sheet() {
    val c = KkTheme.colors
    val t = KkTheme.type
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        KkTopBar(title = "테마 v2", onLeading = {})
        Column(Modifier.weight(1f).padding(horizontal = KkSpacing.screen), verticalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(c.canvas, c.surface, c.bar, c.tag, c.tape, c.ink, c.sub, c.line, c.accent, c.love, c.stamp, c.plant).forEach { Swatch(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(c.danger, c.success, c.eventVacation, c.eventOvernight, c.eventVisit, c.eventTraining, c.eventDuty, c.eventAnniversary).forEach { Swatch(it) }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(KkSpacing.md)) {
                Text("D-128", style = t.ddayM, color = c.ink)
                Text("D-3", style = t.ddayS, color = c.ink)
            }
            Text("민준 · 서연", style = t.title, color = c.ink)
            Box(Modifier.background(c.tag).padding(horizontal = KkSpacing.md, vertical = KkSpacing.xs)) {
                Text("전역까지", style = t.handL, color = c.ink)
            }
            Text("오늘도 보고 싶어. 휴가 나오면 그 카페 가자.", style = t.handLetter, color = c.ink)
            KkCard {
                Text("오늘 온 편지", style = t.labelBold, color = c.ink)
                Text("본문은 고딕이에요. 날짜 · 보낸 시각은 캡션으로.", style = t.body, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                    Text("라벨", style = t.label, color = c.sub)
                    Text("10월 29일 오후 9:12", style = t.caption, color = c.sub)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm)) {
                KkStatusChip("휴가", selected = true, onClick = {}, dot = c.eventVacation)
                KkStatusChip("면회", selected = false, onClick = {}, dot = c.eventVisit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                KkPrimaryButton("보내기", onClick = {}, modifier = Modifier.weight(1f))
                KkSecondaryButton("임시저장", onClick = {}, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                KkMissYouButton(sentToday = 0, onClick = {})
            }
        }
        KkTabBar(
            tabs = listOf(KkTab("홈", KkIcons.Home), KkTab("캘린더", KkIcons.Calendar), KkTab("편지", KkIcons.Mail), KkTab("추억", KkIcons.Album)),
            selected = 0,
            onSelect = {},
        )
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(24.dp).border(1.dp, KkTheme.colors.line, CircleShape).padding(1.dp).background(color, CircleShape))
}
