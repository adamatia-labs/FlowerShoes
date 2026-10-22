package com.sadturtleman.flowershoes.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.databaseBuilder
import okio.Path.Companion.toPath
import org.koin.core.module.Module

/**
 * Android 진입점(:app 의 Application)이 부른다. DB · 설정 파일은 자동 백업에서 빠진다(data_extraction_rules.xml).
 */
fun kkotsinAndroidModules(context: Context): List<Module> {
    val app = context.applicationContext
    return kkotsinModules(
        database = databaseBuilder(app).buildKkotsin(),
        settingsStore = settingsDataStore(app),
        attachmentsRoot = app.filesDir.resolve("attachments").absolutePath.toPath(),
    )
}

private fun settingsDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath {
        context.filesDir.resolve("datastore/kkotsin_device.preferences_pb").absolutePath.toPath()
    }
