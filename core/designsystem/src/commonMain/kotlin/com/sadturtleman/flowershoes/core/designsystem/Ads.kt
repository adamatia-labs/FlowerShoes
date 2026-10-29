package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/**
 * 광고 지면 한 칸(Figma Ad Slot `341:1104`). 캘린더 · 휴가 · 설정에만 둔다.
 * 배너도 줄 광고도 없으면(광고가 오기 전 · 꽃신 평생) 아무것도 그리지 않아 높이가 0 이다.
 *
 * @param slot 어느 지면인지([KkAdSlot]). 공급자가 지면별로 다른 광고를 줄 수 있다.
 */
@Composable
fun KkAd(slot: KkAdSlot, modifier: Modifier = Modifier) {
    LocalAdBanner.current?.let { banner -> banner(slot, modifier); return }
    val ad = LocalAds.current(slot) ?: return
    KkNativeAd(ad, modifier)
}

/** 네이티브 광고(Figma Ad Slot): surface · line 점선 테두리 · 반경 8 · 높이 60, 40 썸네일 + 제목(Label/Bold) · 한 줄(Caption) + 오른쪽 "광고". */
@Composable
fun KkNativeAd(ad: KkAdContent, modifier: Modifier = Modifier) {
    val c = KkTheme.colors
    val shape = RoundedCornerShape(KkRadius.sm)
    Row(
        modifier.fillMaxWidth().height(60.dp).clip(shape).background(c.surface).dashedBorder(c.line, KkRadius.sm)
            .clickable(role = Role.Button, onClick = ad.onClick).padding(horizontal = KkSpacing.md, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(shape).background(c.line)) {
            ad.image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KkSpacing.xxs)) {
            Text(ad.title, style = KkTheme.type.labelBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ad.body, style = KkTheme.type.caption, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("광고", style = KkTheme.type.caption, color = c.sub)
    }
}
