package com.sadturtleman.flowershoes.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sadturtleman.flowershoes.MainActivity
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.designsystem.KkColors
import com.sadturtleman.flowershoes.core.designsystem.kkColors
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.media.DrawingBitmap
import com.sadturtleman.flowershoes.core.widget.SnapshotStore
import com.sadturtleman.flowershoes.core.widget.WidgetSnapshot
import okio.Path.Companion.toPath
import org.koin.core.context.GlobalContext

/**
 * 홈 화면 위젯(설계 8장). 위젯은 DB 를 읽지 않고 스냅샷 파일만 읽는다.
 * 색은 기기 테마를 따르고(스냅샷에 테마가 들어 있다), 라이트/다크는 시스템을 따른다. 보고싶어 위젯만 love 색.
 */
internal fun snapshotStore(context: Context) =
    SnapshotStore(context.filesDir.resolve("widget/snapshot.json").absolutePath.toPath())

private fun AppThemeOf(s: WidgetSnapshot?) = AppTheme.from(s?.theme, role = null)

private class Palette(theme: AppTheme) {
    val light: KkColors = kkColors(theme, dark = false)
    val dark: KkColors = kkColors(theme, dark = true)
    fun of(pick: (KkColors) -> Color) = ColorProvider(day = pick(light), night = pick(dark))
}

/** 모든 위젯이 같은 틀: 둥근 표면 + 누르면 앱 열기. */
@Composable
private fun Card(p: Palette, onClick: androidx.glance.action.Action, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(p.of { it.surface }).padding(14.dp).clickable(onClick),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}

/**
 * 받은 그림 위젯의 프레임(설정 › 위젯, 꽃신 평생). 기본은 [Card] 와 같다.
 * 폴라로이드는 흰 바탕 + 아래를 넓게, 나머지는 바탕 색만 바꾼다.
 */
@Composable
private fun FramedCard(p: Palette, frame: String?, onClick: androidx.glance.action.Action, content: @Composable () -> Unit) {
    val fixed = { c: Long -> ColorProvider(Color(c), Color(c)) }
    val (bg, pad) = when (frame) {
        "BLOSSOM" -> fixed(0xFFFFE0E7) to (14.dp to 14.dp)
        "KRAFT" -> fixed(0xFFE9DCC4) to (14.dp to 14.dp)
        "POLAROID" -> fixed(0xFFFFFFFF) to (10.dp to 30.dp)
        "NIGHT" -> fixed(0xFF1B2235) to (14.dp to 14.dp)
        else -> return Card(p, onClick, content)
    }
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(bg)
            .padding(start = pad.first, end = pad.first, top = pad.first, bottom = pad.second).clickable(onClick),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}

// ── 전역 D-day ──────────────────────────────────────────────────────────

class DdayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = Palette(AppThemeOf(s))
        provideContent {
            GlanceTheme {
                Card(p, actionStartActivity<MainActivity>()) {
                    Column {
                        Text("전역까지", style = TextStyle(color = p.of { it.sub }, fontSize = 13.sp))
                        Spacer(GlanceModifier.height(4.dp))
                        val d = s?.daysLeft
                        Text(
                            when {
                                d == null -> "입대일을 넣어 주세요"
                                d > 0 -> "D-$d"
                                d == 0 -> "D-DAY"
                                else -> "전역 +${-d}"
                            },
                            style = TextStyle(color = p.of { it.ink }, fontSize = if (d == null) 14.sp else 34.sp, fontWeight = FontWeight.Bold),
                        )
                        s?.progress?.let {
                            Spacer(GlanceModifier.height(6.dp))
                            Text("${(it * 1000).toInt() / 10.0}% · ${s.dischargeDate.orEmpty()}", style = TextStyle(color = p.of { c -> c.accent }, fontSize = 12.sp))
                        }
                    }
                }
            }
        }
    }
}

class DdayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DdayWidget()
}

// ── 보고싶어(누르면 바로 보냄) ────────────────────────────────────────────

class MissYouWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = Palette(AppThemeOf(s))
        provideContent {
            GlanceTheme {
                Box(
                    GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(p.of { it.love }).padding(14.dp)
                        .clickable(actionRunCallback<SendMissYouAction>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(ImageProvider(com.sadturtleman.flowershoes.R.drawable.ic_heart_white), contentDescription = null)
                        Text("보고싶어", style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        if ((s?.missYouFromPartnerToday ?: 0) > 0) {
                            Text("오늘 받은 ${s!!.missYouFromPartnerToday}번", style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 11.sp))
                        }
                    }
                }
            }
        }
    }
}

class MissYouWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MissYouWidget()
}

/** Android 위젯 콜백은 앱 프로세스에서 돈다 → 일반 쓰기 경로(Outbox)로 보낸다. */
class SendMissYouAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        GlobalContext.getOrNull()?.get<MissYouRepository>()?.send()
        MissYouWidget().updateAll(context)
    }
}

// ── 다음 만남 ────────────────────────────────────────────────────────────

class NextMeetingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = Palette(AppThemeOf(s))
        provideContent {
            GlanceTheme {
                Card(p, actionStartActivity<MainActivity>()) {
                    Column {
                        Text("다음 만남", style = TextStyle(color = p.of { it.sub }, fontSize = 13.sp))
                        Spacer(GlanceModifier.height(4.dp))
                        val days = s?.nextEventDays
                        if (days == null) {
                            Text("아직 잡힌 일정이 없어요", style = TextStyle(color = p.of { it.sub }, fontSize = 13.sp))
                        } else {
                            Text(if (days == 0) "오늘" else "D-$days", style = TextStyle(color = p.of { it.accent }, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                            Text(s.nextEventTitle.orEmpty(), style = TextStyle(color = p.of { it.ink }, fontSize = 13.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class NextMeetingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextMeetingWidget()
}

// ── 받은 그림 ────────────────────────────────────────────────────────────

/** 위젯은 애니메이션이 안 되므로 완성본 + "▶ 과정". 누르면 그 편지가 열려 리플레이를 볼 수 있다. */
class DrawingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshotStore(context).read()
        val p = Palette(AppThemeOf(s))
        // 완성 PNG 가 있으면 그대로, 없으면(옛 그림) 문서를 그 자리에서 그린다.
        val bitmap: Bitmap? = s?.drawingPngPath?.let { android.graphics.BitmapFactory.decodeFile(it) }
            ?: s?.drawingDocPath?.let { DrawingBitmap.render(it, widthPx = 680) }
        provideContent {
            GlanceTheme {
                val open = s?.drawingLetterId?.let {
                    androidx.glance.appwidget.action.actionStartActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("kkotsin://letter/$it"), context, MainActivity::class.java),
                    )
                } ?: actionStartActivity<MainActivity>()
                FramedCard(p, s?.widgetFrame, open) {
                    if (bitmap == null) {
                        Column {
                            Text("받은 그림", style = TextStyle(color = p.of { it.sub }, fontSize = 13.sp))
                            Spacer(GlanceModifier.height(4.dp))
                            Text("연인이 그림을 보내면 여기에 보여요", style = TextStyle(color = p.of { it.sub }, fontSize = 13.sp))
                        }
                    } else {
                        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                            Image(ImageProvider(bitmap), contentDescription = "받은 그림", modifier = GlanceModifier.fillMaxSize().cornerRadius(12.dp), contentScale = ContentScale.Fit)
                            Text("▶", style = TextStyle(color = ColorProvider(Color.White, Color.White), fontSize = 11.sp),
                                modifier = GlanceModifier.cornerRadius(10.dp).background(ColorProvider(Color(0x99000000), Color(0x99000000))).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

class DrawingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DrawingWidget()
}

/** 스냅샷이 바뀌면 앱이 부른다. */
suspend fun updateAllWidgets(context: Context) {
    DdayWidget().updateAll(context)
    MissYouWidget().updateAll(context)
    NextMeetingWidget().updateAll(context)
    DrawingWidget().updateAll(context)
}
