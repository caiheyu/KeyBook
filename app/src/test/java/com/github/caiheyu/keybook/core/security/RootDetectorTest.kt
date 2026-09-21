package com.github.caiheyu.keybook.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootDetectorTest {
    @Test
    fun reportsKnownRootFilesWithoutExecutingThem() {
        assertTrue(
            RootDetector.hasRootIndicators(null, { it == "/system/xbin/su" }, includeTestKeys = false),
        )
        assertFalse(RootDetector.hasRootIndicators("release-keys", { false }, includeTestKeys = true))
    }

    @Test
    fun testKeysAreIgnoredOnlyForDebugBuildChecks() {
        assertTrue(RootDetector.hasRootIndicators("dev-keys,test-keys", { false }, includeTestKeys = true))
        assertFalse(RootDetector.hasRootIndicators("dev-keys,test-keys", { false }, includeTestKeys = false))
    }
}
