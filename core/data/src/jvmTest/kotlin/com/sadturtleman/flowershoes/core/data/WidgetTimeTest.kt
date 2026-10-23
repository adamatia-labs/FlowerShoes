package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 추억 › 그림 상세 "위젯에 n시간 표시됨": 그림이 위젯에 떠 있던 시간을 바뀔 때마다 닫아 더한다. */
class WidgetTimeTest {
    private val hour = 3_600_000L

    @Test
    fun `위젯에 떠 있던 시간을 그림마다 더하고 지금 떠 있는 그림은 지금까지 센다`() = runTest {
        val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/kkotsin-widget-${Uuid.random()}.preferences_pb").toPath()
        val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath { path })

        settings.widgetShowing("d1", 0)
        settings.widgetShowing("d1", 1 * hour) // 같은 그림이면 그대로
        settings.widgetShowing("d2", 6 * hour)
        settings.widgetShowing(null, 8 * hour) // 위젯을 뺐다
        settings.widgetShowing("d1", 10 * hour)

        val shown = settings.widgetShownMs { 11 * hour }.first()
        assertEquals(7 * hour, shown["d1"]) // 0–6 + 10–11
        assertEquals(2 * hour, shown["d2"])
        FileSystem.SYSTEM.delete(path)
    }
}
