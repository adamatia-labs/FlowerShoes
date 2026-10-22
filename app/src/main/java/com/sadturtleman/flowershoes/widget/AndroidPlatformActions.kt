package com.sadturtleman.flowershoes.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sadturtleman.flowershoes.app.auth.CurrentActivity
import com.sadturtleman.flowershoes.feature.settings.DeviceAuth
import com.sadturtleman.flowershoes.feature.settings.PlatformActions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** 설정의 플랫폼 기능: 위젯 고정 요청, 기기 생체 · 암호 인증. */
class AndroidPlatformActions(private val context: Context) : PlatformActions {

    private val receivers = linkedMapOf(
        "dday" to ("전역 D-day" to DdayWidgetReceiver::class.java),
        "missyou" to ("보고싶어" to MissYouWidgetReceiver::class.java),
        "next" to ("다음 만남" to NextMeetingWidgetReceiver::class.java),
        "drawing" to ("받은 그림" to DrawingWidgetReceiver::class.java),
    )

    override val pinnableWidgets: List<Pair<String, String>>
        get() = if (AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported) receivers.map { (k, v) -> k to v.first } else emptyList()

    override fun requestPinWidget(kind: String): Boolean {
        val target = receivers[kind]?.second ?: return false
        return AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, target), null, null)
    }

    override val keySyncName: String get() = "Google 계정 백업"

    override suspend fun deleteSyncedBackupKey() {
        com.sadturtleman.flowershoes.app.backup.BlockStoreKeySync(context).delete()
    }

    /** BIOMETRIC_STRONG or DEVICE_CREDENTIAL — 지문 · 얼굴이 없으면 PIN · 패턴으로. */
    override suspend fun authenticate(reason: String): DeviceAuth {
        val activity = CurrentActivity.activity as? FragmentActivity ?: return DeviceAuth.Failed
        val allowed = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        if (BiometricManager.from(activity).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) return DeviceAuth.NoLockScreen
        return suspendCancellableCoroutine { c ->
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (c.isActive) c.resume(DeviceAuth.Passed)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (c.isActive) c.resume(DeviceAuth.Failed)
                }
            })
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder().setTitle("본인 확인").setSubtitle(reason).setAllowedAuthenticators(allowed).build(),
            )
        }
    }
}
