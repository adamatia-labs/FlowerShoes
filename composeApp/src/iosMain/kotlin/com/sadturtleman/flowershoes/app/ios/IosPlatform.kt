@file:OptIn(ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.app.ios

import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.sync.MailItem
import com.sadturtleman.flowershoes.core.data.sync.SyncClient
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.notification.LocalNotification
import com.sadturtleman.flowershoes.core.notification.NotificationScheduler
import com.sadturtleman.flowershoes.feature.settings.DeviceAuth
import com.sadturtleman.flowershoes.feature.settings.PlatformActions
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear

// iOS 플랫폼 구현. macOS 에서 빌드 · 실기기 확인이 필요하다(Windows 에서는 iOS 타깃이 컴파일되지 않는다).

const val APP_GROUP = "group.com.sadturtleman.kkotsin"

/**
 * App Group 컨테이너. 위젯 스냅샷 · 받은 Op 대기열 · link.json 이 여기 있다(SharedStore.swift 와 같은 경로).
 * App Group 권한이 없는 빌드(서명 없는 시뮬레이터 빌드 · 설정 누락)에서는 앱 자체 폴더를 쓴다 —
 * 위젯 · 알림 확장과 공유되지 않을 뿐 앱은 그대로 동작한다.
 */
fun appGroupRoot(): Path {
    NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP)?.path?.let { return it.toPath() }
    val support = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    val fallback = (requireNotNull(support?.path) + "/shared").toPath()
    FileSystem.SYSTEM.createDirectories(fallback)
    println("Kkotsin: App Group 이 없어 앱 폴더를 쓴다 — 위젯 · 알림 확장과 공유되지 않는다")
    return fallback
}

/** 기기 인증: LAContext .deviceOwnerAuthentication(Face ID · Touch ID, 없으면 기기 암호). */
class IosPlatformActions : PlatformActions {
    override val keySyncName: String get() = "iCloud 키체인"

    override val deviceName: String get() = platform.UIKit.UIDevice.currentDevice.model

    override val osVersion: String get() = "iOS ${platform.UIKit.UIDevice.currentDevice.systemVersion}"

    override val appVersion: String
        get() = (platform.Foundation.NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "1.0.0"

    /** 알림 확장(NotificationService.swift)이 이 파일이 있으면 상태 문구를 풀지 않는다. */
    override fun setHidePreview(hide: Boolean) {
        val flag = appGroupRoot() / "prefs/hide_preview"
        runCatching {
            if (hide) {
                okio.FileSystem.SYSTEM.createDirectories(flag.parent!!)
                okio.FileSystem.SYSTEM.write(flag) { writeUtf8("1") }
            } else {
                okio.FileSystem.SYSTEM.delete(flag, mustExist = false)
            }
        }
    }

    override suspend fun authenticate(reason: String): DeviceAuth {
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)) return DeviceAuth.NoLockScreen
        return suspendCancellableCoroutine { c ->
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { ok, _ ->
                if (c.isActive) c.resume(if (ok) DeviceAuth.Passed else DeviceAuth.Failed)
            }
        }
    }
}

/** 로컬 알림(UNUserNotificationCenter). 앱당 64개 한도라 계획기가 50개만 넘긴다. */
class IosNotificationScheduler : NotificationScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        center.removeAllPendingNotificationRequests()
        notifications.forEach { n ->
            val content = UNMutableNotificationContent().apply {
                setTitle(n.title)
                setBody(n.body)
                setUserInfo(mapOf("link" to n.deepLink))
            }
            val date = NSDate.dateWithTimeIntervalSince1970(n.at.toEpochMilliseconds() / 1000.0)
            val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or NSCalendarUnitMinute
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(NSCalendar.currentCalendar.components(units, date), repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(n.id, content, trigger), null)
        }
    }
}

/** 위젯 전송(App Intent)이 읽는 연결 정보. WidgetSender.swift 의 LinkConfig 와 같은 필드. */
@Serializable
data class LinkConfig(val coupleId: String, val userId: String, val deviceId: String, val partnerDeviceId: String, val epoch: Int)

/** 연결 · 기기 교체 · 키 교체 뒤에 부른다. */
suspend fun writeLinkConfig(db: AppDatabase, identity: Identity, root: Path = appGroupRoot(), fs: FileSystem = FileSystem.SYSTEM) {
    val couple = db.coupleDao().get() ?: return
    val partner = db.deviceDao().activeFor(couple.partnerUserId) ?: return
    val config = LinkConfig(couple.id, identity.userId, identity.deviceId, partner.id, couple.keyEpoch)
    fs.write(root / "link.json") { writeUtf8(Json.encodeToString(LinkConfig.serializer(), config)) }
}

/** NSE 가 App Group 에 남긴 받은 Op(NotificationService.swift 의 queued JSON). */
@Serializable
private data class QueuedOp(val opId: String, val stream: String, val seq: Long, val header: String, val ct: String, val sig: String, val kind: String)

/**
 * 앱이 앞으로 나올 때: NSE 가 받아 둔 Op 을 DB 에 합치고 파일을 지운다.
 * 같은 Op 이 우편함에서도 오면 (stream, seq) 로 걸러진다.
 */
suspend fun mergeNseQueue(sync: SyncClient, root: Path = appGroupRoot(), fs: FileSystem = FileSystem.SYSTEM) {
    val dir = root / "inbox-queue"
    if (!fs.exists(dir)) return
    val json = Json { ignoreUnknownKeys = true }
    fs.list(dir).forEach { file ->
        val q = runCatching { json.decodeFromString(QueuedOp.serializer(), fs.read(file) { readUtf8() }) }.getOrNull()
        if (q != null) {
            val from = q.stream.substringBeforeLast(':')
            sync.receiveQueued(MailItem(q.opId, q.kind, from, Base64.decode(q.header), Base64.decode(q.ct), Base64.decode(q.sig)))
        }
        fs.delete(file)
    }
}

/**
 * 받은 그림 PNG 는 앱의 Application Support 에 있어 위젯 확장이 못 읽는다 — App Group(widget/drawing.png)로 복사하고
 * 스냅샷의 경로를 그쪽으로 바꾼다. 같은 그림이면 다시 복사하지 않는다.
 */
fun mirrorWidgetDrawing(store: com.sadturtleman.flowershoes.core.widget.SnapshotStore, root: Path = appGroupRoot(), fs: FileSystem = FileSystem.SYSTEM) {
    val snap = store.read() ?: return
    val src = snap.drawingPngPath?.toPath() ?: return
    if (src.toString().startsWith(root.toString())) return
    val dir = root / "widget"
    val dst = dir / "drawing-${snap.drawingId ?: "latest"}.png"
    runCatching {
        fs.createDirectories(dir)
        if (!fs.exists(dst)) {
            fs.list(dir).filter { it.name.startsWith("drawing-") && it != dst }.forEach { fs.delete(it) }
            fs.copy(src, dst)
        }
        store.write(snap.copy(drawingPngPath = dst.toString()))
    }
}
