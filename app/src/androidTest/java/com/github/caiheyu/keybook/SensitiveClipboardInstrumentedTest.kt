package com.github.caiheyu.keybook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.github.caiheyu.keybook.core.clipboard.SensitiveClipboard
import com.github.caiheyu.keybook.data.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitiveClipboardInstrumentedTest {
    @Test
    fun sensitiveCopyCarriesOwnershipAndExpiryButPlainCopyDoesNot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            DebugClipboardEntryPoint::class.java,
        )
        val settings = entryPoint.settingsRepository()
        runBlocking { settings.clearClipboardOwnership() }
        lateinit var manager: ClipboardManager
        lateinit var clipboard: SensitiveClipboard
        lateinit var sensitiveClip: ClipData
        lateinit var plainClip: ClipData

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val focusDeadline = SystemClock.uptimeMillis() + 5_000L
            var hasFocus = false
            while (!hasFocus && SystemClock.uptimeMillis() < focusDeadline) {
                scenario.onActivity { hasFocus = it.hasWindowFocus() }
                if (!hasFocus) SystemClock.sleep(50L)
            }
            assertTrue("MainActivity did not receive window focus", hasFocus)
            scenario.onActivity {
                manager = context.getSystemService(ClipboardManager::class.java)
                clipboard = entryPoint.sensitiveClipboard()
                clipboard.onForeground()
                sensitiveClip = clipboard.copy("密码", "sensitive-test-value", sensitive = true)
            }

            assertEquals("sensitive-test-value", sensitiveClip.getItemAt(0).text.toString())
            val ownershipToken = sensitiveClip.description.extras?.getString(OWNER_TOKEN_KEY)
            assertTrue(ownershipToken?.isNotEmpty() == true)
            assertTrue(sensitiveClip.description.label?.toString()?.startsWith(OWNER_LABEL_PREFIX) == true)
            val persisted = runBlocking {
                withTimeout(5_000L) {
                    var value = settings.getClipboardOwnership()
                    while (value == null) {
                        delay(20L)
                        value = settings.getClipboardOwnership()
                    }
                    value
                }
            }
            assertEquals(ownershipToken, persisted.token)
            runBlocking {
                withTimeout(5_000L) {
                    while (manager.primaryText() != "sensitive-test-value") delay(20L)
                }
            }

            scenario.onActivity {
                plainClip = clipboard.copy("账号", "plain-test-value", sensitive = false)
            }
            assertFalse(plainClip.description.extras?.containsKey(OWNER_TOKEN_KEY) == true)
            assertFalse(plainClip.description.label?.toString()?.startsWith(OWNER_LABEL_PREFIX) == true)
            runBlocking {
                withTimeout(5_000L) {
                    while (settings.getClipboardOwnership() != null) delay(20L)
                }
            }
            runBlocking {
                withTimeout(5_000L) {
                    while (manager.primaryText() != "plain-test-value") delay(20L)
                }
            }
            assertEquals("plain-test-value", plainClip.getItemAt(0).text.toString())
            assertTrue(manager.hasPrimaryClip())
            scenario.onActivity { clipboard.onBackground() }
        }
    }

    @Test
    fun copyStartedInForegroundStillPublishesAfterImmediateBackground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            DebugClipboardEntryPoint::class.java,
        )
        val settings = entryPoint.settingsRepository()
        runBlocking { settings.clearClipboardOwnership() }
        lateinit var manager: ClipboardManager
        lateinit var clipboard: SensitiveClipboard

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 5_000L
            var hasFocus = false
            while (!hasFocus && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { hasFocus = it.hasWindowFocus() }
                if (!hasFocus) SystemClock.sleep(50L)
            }
            assertTrue("MainActivity did not receive window focus", hasFocus)
            scenario.onActivity {
                manager = context.getSystemService(ClipboardManager::class.java)
                clipboard = entryPoint.sensitiveClipboard()
                clipboard.onForeground()
                clipboard.copy("密码", "background-transition-value", sensitive = true)
                clipboard.onBackground()
            }

            runBlocking {
                withTimeout(5_000L) {
                    while (settings.getClipboardOwnership() == null ||
                        manager.primaryText() != "background-transition-value"
                    ) delay(20L)
                }
            }

            scenario.onActivity {
                clipboard.onForeground()
                clipboard.copy("账号", "cleanup", sensitive = false)
            }
            runBlocking {
                withTimeout(5_000L) {
                    while (settings.getClipboardOwnership() != null) delay(20L)
                }
            }
            scenario.onActivity { clipboard.onBackground() }
        }
    }

    private fun ClipboardManager.primaryText(): String? =
        primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    companion object {
        private const val OWNER_TOKEN_KEY = "com.github.caiheyu.keybook.clipboard.owner"
        private const val OWNER_LABEL_PREFIX = "KeyBook-sensitive:"
    }
}
