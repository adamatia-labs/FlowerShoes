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
fun kkotsinAndroidModules(
    context: Context,
    authKeys: com.sadturtleman.flowershoes.app.auth.AuthKeys = com.sadturtleman.flowershoes.app.auth.AuthKeys("", ""),
    /** 개발용 로컬 Firebase 에뮬레이터 주소. 비어 있으면 google-services.json 그대로. */
    emulatorHost: String = "",
): List<Module> {
    val app = context.applicationContext
    if (emulatorHost.isNotBlank()) com.sadturtleman.flowershoes.app.auth.EmulatorFirebase.init(app, emulatorHost)
    val firebase = com.google.firebase.FirebaseApp.getApps(app).isNotEmpty()
    (app as? android.app.Application)?.registerActivityLifecycleCallbacks(com.sadturtleman.flowershoes.app.auth.CurrentActivity)
    // Firebase 가 연결된 빌드에서만 실제 로그인. 아니면 로그인 없이 이 기기 안에서만.
    var backupForAuth: com.sadturtleman.flowershoes.core.backup.BackupManager? = null
    val auth: com.sadturtleman.flowershoes.core.auth.AuthGateway =
        if (firebase && emulatorHost.isNotBlank()) {
            com.sadturtleman.flowershoes.app.auth.EmulatorAuthGateway(
                deviceTag = { "dev-" + android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID) },
                hasBackup = { uid -> backupForAuth?.hasBackup(uid) ?: false },
            )
        } else if (firebase) com.sadturtleman.flowershoes.app.auth.AndroidAuthGateway(app, authKeys, hasBackup = { uid -> backupForAuth?.hasBackup(uid) ?: false })
        else com.sadturtleman.flowershoes.core.auth.LocalOnlyAuthGateway()
    val attachments = app.filesDir.resolve("attachments").absolutePath.toPath()
    return kkotsinModules(
        database = databaseBuilder(app).buildKkotsin(),
        settingsStore = settingsDataStore(app),
        attachmentsRoot = attachments,
        auth = auth,
        cloud = firebaseBackendOrNull(app),
        vault = com.sadturtleman.flowershoes.core.data.sync.AndroidKeyVault(app),
    ) + module {
        single { SnapshotStore(app.filesDir.resolve("widget/snapshot.json").absolutePath.toPath()) }
        single { WidgetSnapshotPublisher(get(), get(), get(), get(), get(), get(), get(), get(), attachments, get()) }
        // 백업: 서버 설정이 있으면 Firebase Storage, 없으면 이 폰 안의 파일(암호문)로.
        single {
            val storage: com.sadturtleman.flowershoes.core.backup.BackupStorage =
                if (firebase) com.sadturtleman.flowershoes.app.backup.FirebaseStorageBackup()
                else com.sadturtleman.flowershoes.core.backup.FileBackupStorage(app.filesDir.resolve("backup-local").absolutePath.toPath())
            backupForAuth ?: com.sadturtleman.flowershoes.core.backup.BackupManager(
                storage = storage,
                source = com.sadturtleman.flowershoes.app.backup.AndroidBackupSource(app, get(), get(), get(), get()),
                vault = get(),
                keySync = com.sadturtleman.flowershoes.app.backup.BlockStoreKeySync(app),
                applier = com.sadturtleman.flowershoes.app.backup.AndroidRestoreApplier(app, get(), get(), get(), get()),
                appSchemaVersion = com.sadturtleman.flowershoes.app.backup.AndroidBackupSource.DB_VERSION,
            ).also { backupForAuth = it }
        }
    }
}

/** google-services.json 이 들어간 빌드면 FirebaseApp 이 자동으로 초기화돼 있다. 없으면 혼자 모드만. */
private fun firebaseBackendOrNull(context: Context): CloudBackend? {
    if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) return null
    val transport = com.sadturtleman.flowershoes.core.firebase.FirestoreTransport()
    return CloudBackend(mailbox = transport, blobs = transport, pairing = transport, rotation = com.sadturtleman.flowershoes.core.firebase.FirestoreRotation(), account = com.sadturtleman.flowershoes.core.firebase.FunctionsAccount(), relink = com.sadturtleman.flowershoes.core.firebase.FirestoreRelink())
}

private fun settingsDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath {
        context.filesDir.resolve("datastore/kkotsin_device.preferences_pb").absolutePath.toPath()
    }
