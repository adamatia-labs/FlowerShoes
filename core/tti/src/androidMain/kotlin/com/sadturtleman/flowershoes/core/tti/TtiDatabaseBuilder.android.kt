package com.sadturtleman.flowershoes.core.tti

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sadturtleman.flowershoes.core.tti.room.TtiDatabase

/** 앱 전용 databases/ 폴더. */
fun ttiDatabaseBuilder(context: Context): RoomDatabase.Builder<TtiDatabase> {
    val app = context.applicationContext
    return Room.databaseBuilder<TtiDatabase>(
        context = app,
        name = app.getDatabasePath(TtiDatabase.FILE_NAME).absolutePath,
    )
}
