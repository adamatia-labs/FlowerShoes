package com.sadturtleman.flowershoes.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

@Composable
internal fun CalendarNoticeHost(notices: Flow<String>, content: @Composable () -> Unit) {
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notices) { notices.collect { notice = it } }
    LaunchedEffect(notice) { if (notice != null) { delay(2_500); notice = null } }
    Box(Modifier.fillMaxSize()) {
        content()
        notice?.let {
            KkToast(it, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg))
        }
    }
}
