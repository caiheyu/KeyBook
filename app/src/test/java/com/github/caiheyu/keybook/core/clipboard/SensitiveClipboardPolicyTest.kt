package com.github.caiheyu.keybook.core.clipboard

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveClipboardPolicyTest {
    @Test
    fun clipboardChangeReconciliationKeepsMatchingOrUnavailableOwnership() {
        val expected = ClipboardOwnership(UUID.randomUUID().toString(), expiresAt = 10_000L)

        assertFalse(
            shouldForgetOwnershipAfterClipboardChange(
                expected,
                expected,
                clipboardContentAvailable = true,
            ),
        )
        assertFalse(
            shouldForgetOwnershipAfterClipboardChange(
                expected,
                current = null,
                clipboardContentAvailable = false,
            ),
        )
        assertTrue(
            shouldForgetOwnershipAfterClipboardChange(
                expected,
                expected.copy(token = UUID.randomUUID().toString()),
                clipboardContentAvailable = true,
            ),
        )
        assertTrue(
            shouldForgetOwnershipAfterClipboardChange(
                expected,
                current = null,
                clipboardContentAvailable = true,
            ),
        )
    }

    @Test
    fun restartBeforeExpirySchedulesOnlyTheRemainingLifetime() {
        val ownership = ClipboardOwnership(UUID.randomUUID().toString(), expiresAt = 10_000L)

        assertEquals(4_000L, remainingClipboardLifetime(ownership, ownership, now = 6_000L))
        assertEquals(0L, remainingClipboardLifetime(ownership, ownership, now = 12_000L))
        assertNull(
            remainingClipboardLifetime(
                ownership,
                ownership.copy(token = UUID.randomUUID().toString()),
                now = 6_000L,
            ),
        )
        assertNull(remainingClipboardLifetime(ownership, current = null, now = 6_000L))
    }

    @Test
    fun copiedOwnershipLabelCannotClaimDifferentContent() {
        val token = UUID.randomUUID().toString()
        val expiry = 10_000L
        val original = "original-sensitive-value"
        val label = ClipboardOwnershipCodec.ownerLabel(
            token,
            expiry,
            ClipboardOwnershipCodec.contentHash(original),
        )

        assertEquals(
            ClipboardOwnership(token, expiry),
            ClipboardOwnershipCodec.parseLabel(label, original),
        )
        assertNull(ClipboardOwnershipCodec.parseLabel(label, "later-external-value"))
    }
}
