package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 네이티브 광고 한 칸의 내용(Figma 광고 지면: 썸네일 · 제목 · 설명 · "광고"). */
data class KkAdContent(val title: String, val body: String, val image: ImageBitmap? = null, val onClick: () -> Unit = {})

/** 광고 지면(Figma 9 · 광고 지면 요약). 꽃신 평생이면 모두 숨긴다. */
enum class KkAdSlot { CALENDAR, LEAVE, SETTINGS }

/**
 * 광고 공급자. 광고 SDK 를 붙이면 앱이 이 값을 바꿔 넣는다 — 그 전(그리고 꽃신 평생)에는 null 이라 자리가 비어 있다.
 */
val LocalAds = staticCompositionLocalOf<(KkAdSlot) -> KkAdContent?> { { null } }

/**
 * 플랫폼 배너(Android: AdMob 적응형 배너). 앱이 넣어 주면 [KkAd] 가 네이티브 내용 대신 이것을 그린다.
 * 배너는 광고가 오기 전까지 높이가 0 이라 빈 자리가 생기지 않는다.
 */
val LocalAdBanner = staticCompositionLocalOf<(@Composable (slot: KkAdSlot, modifier: Modifier) -> Unit)?> { null }

/** 지면 한 칸. 배너도 줄 광고도 없으면 아무것도 그리지 않는다. */
@Composable
fun KkAd(slot: KkAdSlot, modifier: Modifier = Modifier) {
    LocalAdBanner.current?.let { banner -> banner(slot, modifier); return }
    val ad = LocalAds.current(slot) ?: return
    KkNativeAd(ad, modifier)
}

/** 네이티브 배너(흰 카드 · 반경 12 · 44 썸네일 · 오른쪽 "광고" 테두리 배지). */
@Composable
fun KkNativeAd(ad: KkAdContent, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.lg)
    Row(
        modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)
            .clickable(role = Role.Button, onClick = ad.onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(KkRadius.md)).background(c.bar)) {
            ad.image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ad.title, style = KkTheme.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ad.body, style = KkTheme.type.caption, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "광고", style = KkTheme.type.caption, color = c.sub,
            modifier = Modifier.border(1.dp, c.line, RoundedCornerShape(KkRadius.sm)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
