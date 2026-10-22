package com.sadturtleman.flowershoes.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.databaseBuilder
import okio.Path.Companion.toPath
import com.sadturtleman.flowershoes.core.widget.SnapshotStore
import com.sadturtleman.flowershoes.core.widget.WidgetSnapshotPublisher
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android 진입점(:app 의 Application)이 부른다. DB · 설정 파일은 자동 백업에서 빠진다(data_extraction_rules.xml).
 */
fun kkotsinAndroidModules(context: Context): List<Module> {
    val app = context.applicationContext
    val attachments = app.filesDir.resolve("attachments").absolutePath.toPath()
    return kkotsinModules(
        database = databaseBuilder(app).buildKkotsin(),
        settingsStore = settingsDataStore(app),
        attachmentsRoot = attachments,
        cloud = firebaseBackendOrNull(app),
    ) + module {
        single { SnapshotStore(app.filesDir.resolve("widget/snapshot.json").absolutePath.toPath()) }
        single { WidgetSnapshotPublisher(get(), get(), get(), get(), get(), get(), get(), get(), attachments, get()) }
    }
}

/** google-services.json 이 들어간 빌드면 FirebaseApp 이 자동으로 초기화돼 있다. 없으면 혼자 모드만. */
private fun firebaseBackendOrNull(context: Context): CloudBackend? {
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) return null
    val transport = com.sadturtleman.flowershoes.core.firebase.FirestoreTransport()
    return CloudBackend(mailbox = transport, blobs = transport, pairing = transport)
}

private fun settingsDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath {
        context.filesDir.resolve("datastore/kkotsin_device.preferences_pb").absolutePath.toPath()
    }
