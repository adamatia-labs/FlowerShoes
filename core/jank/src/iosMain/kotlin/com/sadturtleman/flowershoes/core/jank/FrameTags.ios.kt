package com.sadturtleman.flowershoes.core.jank

import androidx.compose.runtime.Composable

/** iOS 프레임에는 꼬리표를 실을 자리가 없다. 화면 이름은 [JankReporter] 가 들고 있다. */
@Composable
internal actual fun rememberFrameTags(): FrameTags? = null
