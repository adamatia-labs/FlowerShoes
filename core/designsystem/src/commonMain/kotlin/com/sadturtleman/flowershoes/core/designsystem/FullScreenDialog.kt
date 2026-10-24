package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.ui.window.DialogProperties

/** 화면 안쪽에서 안전 영역을 처리하므로 iOS 다이얼로그 창은 화면 끝까지 그린다. */
expect fun fullScreenDialogProperties(): DialogProperties
