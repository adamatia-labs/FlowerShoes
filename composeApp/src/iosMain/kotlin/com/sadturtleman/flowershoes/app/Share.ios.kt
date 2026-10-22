package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
internal actual fun rememberPlatformShare(): (String) -> Unit = remember {
    { text ->
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(controller, animated = true, completion = null)
    }
}
