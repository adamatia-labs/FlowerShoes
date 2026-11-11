package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sadturtleman.flowershoes.core.data.DeviceState
import com.sadturtleman.flowershoes.core.data.ScreenData
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import org.koin.compose.getKoin
import org.koin.compose.scope.KoinScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.qualifier.named
import kotlin.uuid.Uuid

/** 화면 Repository만 별도로 제공한다. 전역 서비스의 의존성을 덮어쓰지 않는다. */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun ScreenDataScope(account: DeviceState?, couple: CoupleEntity?, content: @Composable () -> Unit) {
    if (account == null || !account.onboarded) { content(); return }
    val koin = getKoin()
    val scope = remember(koin, account.userId, account.deviceId, account.accountSession, couple) {
        koin.createScope("screen-${Uuid.random()}", named("screen-data")).apply {
            declare(ScreenData(koin.get(), koin.get(), account, couple, koin.get(), koin.get()))
        }
    }
    KoinScope(scopeDefinition = { scope }, content = content)
}
