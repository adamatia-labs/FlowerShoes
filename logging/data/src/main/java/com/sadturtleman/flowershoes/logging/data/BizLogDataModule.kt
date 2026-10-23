package com.sadturtleman.flowershoes.logging.data

import com.sadturtleman.flowershoes.common.di.IoDispatcher
import com.sadturtleman.flowershoes.logging.domain.BizLogClock
import com.sadturtleman.flowershoes.logging.domain.BizLogShooter
import com.sadturtleman.flowershoes.logging.domain.BizLogger
import com.sadturtleman.flowershoes.logging.domain.SystemBizLogClock
import com.sadturtleman.flowershoes.logging.domain.createBizLogger
import org.koin.dsl.module

val bizLogDataModule = module {
    single<BizLogShooter> { PrintBizLogShooter() }
    single<BizLogClock> { SystemBizLogClock() }
    single<BizLogger> { createBizLogger(get(), get(), get(IoDispatcher)) }
}
