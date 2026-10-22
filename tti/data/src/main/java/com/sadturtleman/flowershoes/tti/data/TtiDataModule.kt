package com.sadturtleman.flowershoes.tti.data

import androidx.room.Room
import com.sadturtleman.flowershoes.common.di.IoDispatcher
import com.sadturtleman.flowershoes.tti.data.room.TtiDatabase
import com.sadturtleman.flowershoes.tti.domain.TtiClock
import com.sadturtleman.flowershoes.tti.domain.TtiRecordStore
import com.sadturtleman.flowershoes.tti.domain.TtiRecorder
import com.sadturtleman.flowershoes.tti.domain.createTtiRecorder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** TTI 기록 저장 · 시계 · 기록기. 전송(TtiShooter)은 앱 모듈이 꽂는다. */
val ttiDataModule = module {
    single {
        Room.databaseBuilder(androidContext(), TtiDatabase::class.java, "tti.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<TtiDatabase>().ttiDao() }
    single<TtiRecordStore> { RoomTtiRecordStore(get()) }
    single<TtiClock> { AndroidTtiClock() }
    single<TtiRecorder> { createTtiRecorder(get(), get(), get(), get(IoDispatcher)) }
}
