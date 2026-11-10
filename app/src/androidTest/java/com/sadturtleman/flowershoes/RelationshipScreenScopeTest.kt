package com.sadturtleman.flowershoes

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.app.RelationshipScreenScope
import com.sadturtleman.flowershoes.core.data.DeviceState
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelationshipScreenScopeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val initial = DeviceState("owner", "phone", null, AppTheme.GOMSIN, true, 1, "session-1")
    private val relation = CoupleEntity("couple-1", "owner", "partner", 1, 1)

    @Test fun recreationKeepsCurrentEditorsAndOnboardingSurvivesLogin() {
        val account = mutableStateOf(initial.copy(onboarded = false, userId = "local:owner"))
        val loaded = mutableStateOf(true)
        var current: PendingEditor? = null
        val screen: @Composable () -> Unit = {
            RelationshipScreenScope(account.value.takeIf { loaded.value }, relation, loaded.value) {
                if (loaded.value) {
                    val vm = ViewModelProvider(checkNotNull(LocalViewModelStoreOwner.current))[PendingEditor::class.java]
                    SideEffect { current = vm }
                    Text(vm.title)
                }
            }
        }
        compose.setContent(screen)
        compose.waitForIdle()
        val onboarding = checkNotNull(current)
        compose.runOnIdle { account.value = initial.copy(onboarded = false) }
        compose.runOnIdle { assertSame(onboarding, current) }
        compose.runOnIdle { account.value = initial }
        compose.runOnIdle { assertNotSame(onboarding, current); assertTrue(onboarding.cleared) }
        val editor = checkNotNull(current)
        compose.runOnIdle { editor.title = "unsaved event"; editor.start() }
        compose.runOnIdle { loaded.value = false }
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { it.setContent(content = screen) }
        compose.runOnIdle { assertFalse(editor.cleared); loaded.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertSame(editor, current); assertFalse(editor.cleared); assertFalse(editor.cancelled) }
        compose.onNodeWithText("unsaved event").assertExists()
    }

    @Test fun sessionSwitchClearsEditorsAndCancelsDelayedWork() {
        val account = mutableStateOf<DeviceState?>(initial)
        val couple = mutableStateOf<CoupleEntity?>(relation)
        var current: PendingEditor? = null
        compose.setContent {
            RelationshipScreenScope(account.value, couple.value) {
                if (account.value != null) {
                    val owner = checkNotNull(LocalViewModelStoreOwner.current)
                    val vm = ViewModelProvider(owner)[PendingEditor::class.java]
                    SideEffect { current = vm }
                    var draft by rememberSaveable { mutableStateOf("new draft") }
                    Text(draft)
                    LaunchedEffect(Unit) { draft = vm.title }
                }
            }
        }
        compose.waitForIdle()
        val first = checkNotNull(current)
        compose.runOnIdle { first.start(); first.title = "old account draft" }

        // A visual preference does not change ownership or cancel an in-progress edit.
        compose.runOnIdle { account.value = initial.copy(theme = AppTheme.SOLDIER) }
        compose.runOnIdle { assertSame(first, current); assertFalse(first.cleared) }
        compose.runOnIdle { account.value = initial.copy(accountSession = "session-2") }
        compose.runOnIdle {
            assertNotSame(first, current)
            assertTrue(first.cleared)
            assertTrue(first.cancelled)
            first.release.complete(Unit)
        }
        compose.runOnIdle { assertFalse(first.wrote) }
        compose.onNodeWithText("new draft").assertExists()

        val second = checkNotNull(current)
        compose.runOnIdle { couple.value = relation.copy(id = "couple-2", partnerUserId = "new-partner") }
        compose.runOnIdle { assertNotSame(second, current); assertTrue(second.cleared) }
        val third = checkNotNull(current)
        compose.runOnIdle { account.value = null }
        compose.runOnIdle { assertTrue(third.cleared) }
        compose.runOnIdle { account.value = initial.copy(accountSession = "session-2") }
        compose.runOnIdle { assertNotSame(third, current) }
    }

    class PendingEditor : ViewModel() {
        var title = "new draft"
        var cleared = false
        var cancelled = false
        var wrote = false
        val release = CompletableDeferred<Unit>()
        fun start() = viewModelScope.launch {
            try { release.await(); wrote = true } finally { cancelled = !wrote }
        }
        override fun onCleared() { cleared = true }
    }
}
