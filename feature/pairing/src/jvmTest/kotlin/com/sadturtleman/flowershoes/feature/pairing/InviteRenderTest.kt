package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.sync.Outbox
import com.sadturtleman.flowershoes.core.data.sync.PairingState
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Figma 1 · 연결(343:83)의 초대 화면을 Figma 예시 값으로 그려 build/render 에 남긴다:
 * 연인 초대 · 링크 공유, 초대 받음 · 수락 / 링크 만료, 연결 완료 · 연결 모달 / 편지 보내는 중.
 */
class InviteRenderTest {
    private val vm = PairingViewModel(
        session = null,
        sync = null,
        settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath { File.createTempFile("invite", ".preferences_pb").apply { delete() }.toOkioPath() }),
    )
    private val waiting = PairingUi(
        available = true,
        state = PairingState.Waiting("AbCdEfGhIjKlMnOpQrSt_-", System.currentTimeMillis() + 7L * 24 * 3600 * 1000),
        myName = "지수",
    )

    @Test
    fun `초대 화면을 그린다`() {
        render("invite_send") { ConnectScreen(waiting, vm, {}, {}) }
        render("invite_received") { ConnectScreen(PairingUi(available = true, state = PairingState.Invited("t", "지수")), vm, {}, {}) }
        render("invite_expired") { ConnectScreen(PairingUi(available = true, state = PairingState.Expired), vm, {}, {}) }
        render("invite_linked_modal") { LinkedDialog("민준", Outbox(23, 4, 40)) {} }
        render("invite_sending") { SendingProgress(Outbox(23, 4, 40), Outbox(0, 3, 4), System.currentTimeMillis() - 30_000) }
    }

    private fun render(name: String, content: @Composable () -> Unit) {
        for ((theme, dark) in listOf(AppTheme.GOMSIN to false, AppTheme.SOLDIER to true)) {
            val png = ImageComposeScene(width = 360 * 2, height = 780 * 2, density = Density(2f)) {
                KkotsinTheme(theme = theme, dark = dark) { content() }
            }.use { scene ->
                scene.render()
                scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            }
            val out = File("build/render/${name}_${theme.name.lowercase()}_${if (dark) "dark" else "light"}.png").apply { parentFile.mkdirs(); writeBytes(png) }
            assertTrue(out.length() > 0)
        }
    }
}
