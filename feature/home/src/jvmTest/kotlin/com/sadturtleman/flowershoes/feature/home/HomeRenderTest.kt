package com.sadturtleman.flowershoes.feature.home

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.model.Role
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** 홈 v2(Figma 2 · 홈 350:686)의 상태들을 build/render/home_*.png 로 그려 Figma 와 대조한다. 예시 데이터는 design.md 그대로. */
class HomeRenderTest {
    private val serving = HomeHero(
        HomePhase.SERVING, "민준이 전역까지", "D-479", progress = 0.285f,
        start = "입대 2026.04.20", end = "전역 2028.02.19", percent = "28.5%", months = 21,
    )
    private val linked = HomeState(
        loading = false, role = Role.GOMSIN, linked = true, dateLine = "10월 28일 수요일", stampDate = "2026.10.28", title = "지수 & 민준",
        hero = serving,
        tags = listOf(HomeTag("다음 휴가", "D-17", hand = false), HomeTag("다음 만남", "11월 8일", hand = true)),
        letter = HomeLetter("l", "오늘 당직인데 네 생각나서 몰래 써", "민준 · 오후 9:12"),
        partnerName = "민준",
    )
    private val soldier = linked.copy(
        role = Role.SOLDIER, title = "민준 & 지수", hero = serving.copy(caption = "전역까지"),
        tags = listOf(HomeTag("다음 휴가", "D-17", hand = false), HomeTag("상병 진급 예정", "D-53", hand = false)),
        letter = HomeLetter("l", "오늘도 수고했어. 당직 끝나면 꼭 자", "지수 · 오후 9:30"), partnerName = "지수",
    )
    private val soloGomsin = HomeState(
        loading = false, role = Role.GOMSIN, dateLine = "10월 28일 수요일", stampDate = "2026.10.28", title = "지수의 꽃신",
        hero = serving.copy(caption = "전역까지"),
        pendingLetters = listOf(
            HomeLetter("b", "처음 쓰는 편지, 연결되면 같이 읽자", "지수 · 오늘 오후 8:40"),
            HomeLetter("a", "휴가 때 뭐 할지 적어봤어", "지수 · 10월 26일"),
        ),
    )
    private val soloSoldier = soloGomsin.copy(
        role = Role.SOLDIER, title = "전역일 계산기", hero = serving.copy(caption = "민준 전역까지"), pendingLetters = emptyList(),
        ranks = listOf(RankRow("일병", "2026.06.20 · 지남"), RankRow("상병(예정)", "2026.12.20 · D-53"), RankRow("병장(예정)", "2027.06.20 · D-235")),
    )
    private val beforeEnlist = soloGomsin.copy(
        hero = HomeHero(HomePhase.BEFORE_ENLIST, "입대까지", "D-12", detail = "공군 · 입대 2026.11.09 (월)"),
        tags = listOf(HomeTag("입대일", "11월 9일", hand = true), HomeTag("함께한 날", "D+411", hand = false)),
        pendingLetters = emptyList(), enlistNudge = true,
    )
    private val archive = linked.copy(
        dateLine = "3월 10일 금요일",
        hero = HomeHero(HomePhase.ARCHIVE, "함께 견딘", "670일", detail = "입대 2026.04.20 – 전역 2028.02.19 · 보관 모드"),
        tags = emptyList(), letter = HomeLetter("l", "이제 편지 말고 매일 볼 수 있네", "민준 · 3월 9일"),
    )

    @Test
    fun `홈 상태를 그린다`() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            render("home_linked_gomsin_$mode", linked, AppTheme.GOMSIN, dark)
            render("home_linked_soldier_$mode", soldier, AppTheme.SOLDIER, dark)
        }
        render("home_no_letter", linked.copy(letter = null), AppTheme.GOMSIN)
        render("home_day_line", linked.copy(dayLine = DayLinePrompt("e", "어제 면회, 그날의 한 줄 남길까요?")), AppTheme.GOMSIN)
        render("home_solo_gomsin", soloGomsin, AppTheme.GOMSIN)
        render("home_solo_gomsin_invited", soloGomsin.copy(invitePending = true), AppTheme.GOMSIN)
        render("home_solo_soldier", soloSoldier, AppTheme.SOLDIER)
        render("home_before_enlist", beforeEnlist, AppTheme.GOMSIN)
        render("home_before_enlist_invited", beforeEnlist.copy(invitePending = true), AppTheme.GOMSIN)
        render("home_archive", archive, AppTheme.GOMSIN)
        render("home_loading", HomeState(), AppTheme.GOMSIN)
        render("home_no_enlist_date", soloGomsin.copy(hero = null, pendingLetters = emptyList()), AppTheme.GOMSIN)
    }

    private fun render(name: String, state: HomeState, theme: AppTheme, dark: Boolean = false) {
        val png = ImageComposeScene(width = 360 * 2, height = 716 * 2, density = Density(2f)) {
            KkotsinTheme(theme = theme, dark = dark) { HomeScreen(state, {}, {}) }
        }.use { scene ->
            scene.render()
            scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val out = File("build/render/$name.png").apply { parentFile.mkdirs(); writeBytes(png) }
        assertTrue(out.length() > 0)
    }
}
