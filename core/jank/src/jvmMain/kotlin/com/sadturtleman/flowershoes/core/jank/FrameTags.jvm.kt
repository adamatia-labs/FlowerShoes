package com.sadturtleman.flowershoes.core.jank

import androidx.compose.runtime.Composable

/** jvm 은 테스트용이라 프레임 소스가 없다. */
@Composable
internal actual fun rememberFrameTags(): FrameTags? = null
