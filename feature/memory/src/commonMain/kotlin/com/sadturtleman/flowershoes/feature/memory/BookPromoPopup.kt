package com.sadturtleman.flowershoes.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.designsystem.KkElevation
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.designsystem.kkElevation
import com.sadturtleman.flowershoes.core.model.BookCover

/**
 * 추억 책 권유 팝업(Figma Promo Popup `404:482` · 홈 · 전역 D-30 / 전역 당일 · 팝업): 홈 위 ink 35% 딤 가운데.
 * 홈 화면 자체는 건드리지 않는다. 표지 미리보기(두 사람이 고른 표지, 전시용이라 살짝 기울임) + "추억 책 보기 ›",
 * 아래 띠 "다시 보지 않기 | 닫기". 딤을 눌러도 닫기와 같다.
 */
@Composable
fun BookPromoPopup(cover: BookCover, summary: BookSummary?, onOpenBook: () -> Unit, onNever: () -> Unit, onClose: () -> Unit) {
    val c = KkTheme.colors
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(c.ink.copy(alpha = 0.35f)).clickable(interactionSource = none, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(KkRadius.xl)
        Column(
            Modifier.width(300.dp).kkElevation(KkElevation.High, shape).clip(shape).background(c.surface)
                .clickable(interactionSource = none, indication = null) {},
        ) {
            Box(Modifier.fillMaxWidth().height(300.dp).background(c.bar), contentAlignment = Alignment.Center) {
                BookCoverArt(cover, summary, Modifier.width(200.dp).rotate(3f).shadow(8.dp))
            }
            Column(
                Modifier.fillMaxWidth().padding(start = KkSpacing.xxl, end = KkSpacing.xxl, top = 20.dp, bottom = KkSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KkSpacing.sm),
            ) {
                Text("추억 책으로 남겨 볼까요?", style = KkTheme.type.title, color = c.ink)
                Text(
                    "추억 책 보기 ›", style = KkTheme.type.labelBold, color = c.accent,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenBook).padding(KkSpacing.xs),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClick = onNever), contentAlignment = Alignment.Center) {
                    Text("다시 보지 않기", style = KkTheme.type.label, color = c.sub)
                }
                Box(Modifier.width(1.dp).height(20.dp).background(c.line))
                Box(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                    Text("닫기", style = KkTheme.type.label, color = c.ink)
                }
            }
        }
    }
}
