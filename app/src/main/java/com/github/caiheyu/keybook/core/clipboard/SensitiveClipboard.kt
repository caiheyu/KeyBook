package com.github.caiheyu.keybook.core.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import com.github.caiheyu.keybook.data.settings.SettingsRepository

@Singleton
class SensitiveClipboard @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writeMutex = Mutex()
    private var ownedToken: String? = null
    private var expiresAt: Long = 0L
    private var writeVersion: Long = 0L
    private var clipboardGeneration: Long = 0L
    private var inFlightWriteVersion: Long? = null
    private var isForeground = false
    private var writeJob: Job? = null
    private var clearJob: Job? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        clipboardGeneration++
        if (isForeground) scope.launch { reconcileOwnershipAfterClipboardChange() }
    }

    init {
        clipboard.addPrimaryClipChangedListener(clipboardListener)
    }

    fun copy(label: String, value: String, sensitive: Boolean): ClipData {
        val version = ++writeVersion
        writeJob?.cancel()
        clearJob?.cancel()
        inFlightWriteVersion = version
        if (!sensitive) {
            val clip = ClipData.newPlainText(label, value)
            writeJob = scope.launch {
                var published = false
                try {
                    writeMutex.withLock {
                        if (writeVersion != version) return@withLock
                        clipboard.setPrimaryClip(clip)
                        published = true
                        // Clear only after the replacement is published. A failed plain write
                        // must leave an earlier sensitive clip eligible for expiry cleanup.
                        settingsRepository.clearClipboardOwnership()
                        if (writeVersion == version) {
                            ownedToken = null
                            expiresAt = 0L
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                } finally {
                    finishWrite(version, scheduleExpiry = !published)
                }
            }
            return clip
        }
        val previousLocalOwnership = ownedToken?.let { ClipboardOwnership(it, expiresAt) }
        val token = UUID.randomUUID().toString()
        val candidateExpiry = System.currentTimeMillis() + CLEAR_DELAY_MILLIS
        ownedToken = token
        expiresAt = candidateExpiry
        val contentHash = ClipboardOwnershipCodec.contentHash(value)
        val description = ClipDescription(
            ClipboardOwnershipCodec.ownerLabel(token, expiresAt, contentHash),
            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
        ).apply {
            extras = PersistableBundle().apply {
                putString(OWNER_TOKEN_KEY, token)
                putLong(OWNER_EXPIRES_AT_KEY, candidateExpiry)
                putString(OWNER_CONTENT_HASH_KEY, contentHash)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        val clip = ClipData(description, ClipData.Item(value))
        writeJob = scope.launch {
            var previousPersistedOwnership: ClipboardOwnership? = null
            try {
                writeMutex.withLock {
                    if (writeVersion != version || ownedToken != token) return@withLock
                    previousPersistedOwnership = settingsRepository.getClipboardOwnership()
                        ?.let { ClipboardOwnership(it.token, it.expiresAt) }
                    settingsRepository.setClipboardOwnership(token, candidateExpiry)
                    if (writeVersion != version || ownedToken != token) {
                        settingsRepository.clearClipboardOwnership(token)
                        return@withLock
                    }
                    clipboard.setPrimaryClip(clip)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (writeVersion == version && ownedToken == token) {
                        restoreOwnershipAfterFailedWrite(
                        candidate = ClipboardOwnership(token, candidateExpiry),
                        previous = previousLocalOwnership ?: previousPersistedOwnership,
                    )
                }
            } finally {
                finishWrite(
                    version,
                    scheduleExpiry = writeVersion == version && ownedToken != null,
                )
            }
        }
        return clip
    }

    fun onForeground() {
        isForeground = true
        scheduleClear(writeJob)
    }

    fun onBackground() {
        isForeground = false
        clearJob?.cancel()
        clearJob = null
    }

    fun clearExpiredIfOwned() {
        if (isForeground) scheduleClear(writeJob)
    }

    private fun scheduleClear(pendingWrite: Job? = null) {
        clearJob?.cancel()
        clearJob = scope.launch {
            pendingWrite?.join()
            if (isForeground) waitThenClearOwned()
        }
    }

    private fun scheduleClearRetry() {
        if (!isForeground) return
        clearJob = scope.launch {
            delay(RETRY_DELAY_MILLIS)
            if (isForeground) waitThenClearOwned()
        }
    }

    private suspend fun waitThenClearOwned() {
        if (!isForeground) return
        if (inFlightWriteVersion != null) return
        val persisted = settingsRepository.getClipboardOwnership()
        if (inFlightWriteVersion != null) return
        val expected = ownedToken?.let { ClipboardOwnership(it, expiresAt) }
            ?: persisted?.let { ClipboardOwnership(it.token, it.expiresAt) }
            ?: return
        val clipboardRead = runCatching { clipboard.primaryClip }
        if (clipboardRead.isFailure) {
            scheduleClearRetry()
            return
        }
        // API 36 can return null while the app has no input focus. Retain ownership and retry
        // when foreground processing runs again instead of treating this as an external change.
        val clip = clipboardRead.getOrNull() ?: run {
            scheduleClearRetry()
            return
        }
        val ownership = clip.clipboardOwnership()
        val remaining = remainingClipboardLifetime(expected, ownership, System.currentTimeMillis())
        if (remaining == null) {
            if (inFlightWriteVersion != null) return
            forgetOwnership(expected.token)
            return
        }
        if (remaining > 0L) delay(remaining)
        clearExpiredIfOwnedNow()
    }

    private suspend fun clearExpiredIfOwnedNow() {
        if (!isForeground) return
        if (inFlightWriteVersion != null) return
        val persisted = settingsRepository.getClipboardOwnership()
        if (inFlightWriteVersion != null) return
        val expected = ownedToken?.let { ClipboardOwnership(it, expiresAt) }
            ?: persisted?.let { ClipboardOwnership(it.token, it.expiresAt) }
            ?: return
        if (System.currentTimeMillis() < expected.expiresAt) return
        val clipboardRead = runCatching { clipboard.primaryClip }
        if (clipboardRead.isFailure) {
            scheduleClearRetry()
            return
        }
        val clip = clipboardRead.getOrNull() ?: run {
            scheduleClearRetry()
            return
        }
        val ownership = clip.clipboardOwnership()
        if (ownership == null) {
            forgetOwnership(expected.token)
            return
        }
        val cleared = writeMutex.withLock {
            runCatching {
                val observedGeneration = clipboardGeneration
                yield()
                val stillCurrent = clipboard.primaryClip?.clipboardOwnership()
                if (isForeground && inFlightWriteVersion == null &&
                    clipboardGeneration == observedGeneration &&
                    stillCurrent == ownership && expected.token == ownership.token &&
                    expected.expiresAt == ownership.expiresAt
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
        }
        if (cleared) {
            forgetOwnership(expected.token)
        } else if (isForeground && ownedToken == expected.token) {
            scheduleClearRetry()
        }
    }

    private suspend fun reconcileOwnershipAfterClipboardChange() {
        if (!isForeground || inFlightWriteVersion != null) return
        writeMutex.withLock {
            if (!isForeground || inFlightWriteVersion != null) return@withLock
            val persisted = settingsRepository.getClipboardOwnership()
            if (!isForeground || inFlightWriteVersion != null) return@withLock
            val expected = ownedToken?.let { ClipboardOwnership(it, expiresAt) }
                ?: persisted?.let { ClipboardOwnership(it.token, it.expiresAt) }
                ?: return@withLock
            val clipboardRead = runCatching { clipboard.primaryClip }
            if (clipboardRead.isFailure) {
                scheduleClearRetry()
                return@withLock
            }
            val clip = clipboardRead.getOrNull()
            if (clip == null) {
                scheduleClearRetry()
                return@withLock
            }
            val currentOwnership = clip.clipboardOwnership()
            if (!shouldForgetOwnershipAfterClipboardChange(
                    expected,
                    currentOwnership,
                    clipboardContentAvailable = true,
                ) || inFlightWriteVersion != null
            ) {
                return@withLock
            }
            clearJob?.cancel()
            clearJob = null
            forgetOwnership(expected.token)
        }
    }

    private suspend fun forgetOwnership(token: String) {
        if (ownedToken == token) {
            ownedToken = null
            expiresAt = 0L
        }
        settingsRepository.clearClipboardOwnership(token)
    }

    private suspend fun restoreOwnershipAfterFailedWrite(
        candidate: ClipboardOwnership,
        previous: ClipboardOwnership?,
    ) {
        val clipboardRead = runCatching { clipboard.primaryClip }
        if (clipboardRead.isFailure || clipboardRead.getOrNull() == null) {
            // A foreground app can temporarily receive no clip while it lacks input focus.
            // Keep the newest candidate until a later read can distinguish it from an older
            // clip or an external replacement.
            ownedToken = candidate.token
            expiresAt = candidate.expiresAt
            settingsRepository.setClipboardOwnership(candidate.token, candidate.expiresAt)
            return
        }
        val current = clipboardRead.getOrNull()!!.clipboardOwnership()
        when {
            current == candidate -> {
                // The platform may have published the clip before throwing. Keep the
                // candidate so the expiry path can still clear it.
                ownedToken = candidate.token
                expiresAt = candidate.expiresAt
                settingsRepository.setClipboardOwnership(candidate.token, candidate.expiresAt)
            }
            current == previous && previous != null -> {
                ownedToken = previous.token
                expiresAt = previous.expiresAt
                settingsRepository.setClipboardOwnership(previous.token, previous.expiresAt)
            }
            else -> {
                ownedToken = null
                expiresAt = 0L
                settingsRepository.clearClipboardOwnership(candidate.token)
            }
        }
    }

    private fun finishWrite(version: Long, scheduleExpiry: Boolean) {
        if (inFlightWriteVersion == version) {
            inFlightWriteVersion = null
            if (scheduleExpiry && isForeground) {
                scheduleClear()
            } else if (isForeground) {
                scope.launch { reconcileOwnershipAfterClipboardChange() }
            }
        }
    }

    private fun ClipData.clipboardOwnership(): ClipboardOwnership? {
        if (itemCount != 1) return null
        val extras = description.extras
        val extraToken = extras?.getString(OWNER_TOKEN_KEY)
        val extraExpiry = extras?.getLong(OWNER_EXPIRES_AT_KEY, 0L) ?: 0L
        val expectedHash = extras?.getString(OWNER_CONTENT_HASH_KEY)
        val actualText = getItemAt(0).text?.toString() ?: return null
        if (extraToken.isValidOwnerToken() && extraExpiry > 0L &&
            expectedHash != null && ClipboardOwnershipCodec.contentMatches(expectedHash, actualText)
        ) {
            return ClipboardOwnership(extraToken!!, extraExpiry)
        }
        return ClipboardOwnershipCodec.parseLabel(description.label?.toString(), actualText)
    }

    private fun String?.isValidOwnerToken(): Boolean =
        this != null && runCatching { UUID.fromString(this) }.isSuccess

    companion object {
        private const val OWNER_TOKEN_KEY = "com.github.caiheyu.keybook.clipboard.owner"
        private const val OWNER_EXPIRES_AT_KEY = "com.github.caiheyu.keybook.clipboard.expires_at"
        private const val OWNER_CONTENT_HASH_KEY = "com.github.caiheyu.keybook.clipboard.content_hash"
        private const val CLEAR_DELAY_MILLIS = 60_000L
        private const val RETRY_DELAY_MILLIS = 500L
    }
}

internal data class ClipboardOwnership(val token: String, val expiresAt: Long)

internal fun shouldForgetOwnershipAfterClipboardChange(
    expected: ClipboardOwnership,
    current: ClipboardOwnership?,
    clipboardContentAvailable: Boolean,
): Boolean = clipboardContentAvailable && current != expected

internal fun remainingClipboardLifetime(
    expected: ClipboardOwnership,
    current: ClipboardOwnership?,
    now: Long,
): Long? = if (current == expected) (expected.expiresAt - now).coerceAtLeast(0L) else null

internal object ClipboardOwnershipCodec {
    private const val OWNER_LABEL_PREFIX = "KeyBook-sensitive:"

    fun ownerLabel(token: String, expiry: Long, contentHash: String): String =
        "$OWNER_LABEL_PREFIX$token.$expiry.$contentHash"

    fun parseLabel(label: String?, content: String): ClipboardOwnership? {
        val fields = label
            ?.takeIf { it.startsWith(OWNER_LABEL_PREFIX) }
            ?.removePrefix(OWNER_LABEL_PREFIX)
            ?.split('.', limit = 3)
            ?: return null
        if (fields.size != 3) return null
        val token = fields[0]
        val expiry = fields[1].toLongOrNull() ?: return null
        return if (runCatching { UUID.fromString(token) }.isSuccess && expiry > 0L &&
            contentMatches(fields[2], content)
        ) ClipboardOwnership(token, expiry) else null
    }

    fun contentHash(content: String): String = Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8)),
    )

    fun contentMatches(expectedHash: String, content: String): Boolean = MessageDigest.isEqual(
        expectedHash.toByteArray(Charsets.US_ASCII),
        contentHash(content).toByteArray(Charsets.US_ASCII),
    )
}
