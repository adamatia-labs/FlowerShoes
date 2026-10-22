package com.sadturtleman.flowershoes.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 앱 전용 databases/ 폴더. 자동 백업 제외는 앱 모듈의 data_extraction_rules.xml · backup_rules.xml 이 맡는다.
 */
fun databaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val app = context.applicationContext
    return Room.databaseBuilder<AppDatabase>(
        context = app,
        name = app.getDatabasePath(AppDatabase.FILE_NAME).absolutePath,
    )
}
