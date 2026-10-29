package com.sadturtleman.flowershoes.core.tti

import androidx.room.RoomDatabase
import com.sadturtleman.flowershoes.common.di.IoDispatcher
import com.sadturtleman.flowershoes.core.tti.room.TtiDatabase
import com.sadturtleman.flowershoes.core.tti.room.buildTti
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * TTI 기록 저장 · 시계 · 기록기 · 전송.
 *
 * [database] 는 DB 파일 위치만 아는 플랫폼 빌더다 — Android 는 ttiDatabaseBuilder(context), iOS 는 ttiDatabaseBuilder().
 * 전송할 수집 서버가 없어 표준 출력으로 흘린다([PrintTtiShooter]).
 */
fun ttiModule(database: () -> RoomDatabase.Builder<TtiDatabase>): Module = module {
    single { database().buildTti() }
    single<TtiRecordStore> { RoomTtiRecordStore(get<TtiDatabase>().ttiDao()) }
    single<TtiClock> { SystemTtiClock() }
    single<TtiShooter> { PrintTtiShooter() }
    single<TtiRecorder> { createTtiRecorder(get(), get(), get(), get(IoDispatcher)) }
}
