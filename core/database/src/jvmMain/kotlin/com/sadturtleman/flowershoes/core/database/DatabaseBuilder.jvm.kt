package com.sadturtleman.flowershoes.core.database

import androidx.room.Room
import androidx.room.RoomDatabase

/** 테스트용. 실제 SQLite(번들 드라이버)에 쿼리를 돌린다. */
fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> = Room.inMemoryDatabaseBuilder<AppDatabase>()
