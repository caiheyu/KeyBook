package com.github.caiheyu.keybook.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceResultGuardTest {
    @Test
    fun staleWorkspaceResultCannotBeAppliedAfterSwitch() {
        assertFalse(isWorkspaceResultCurrent("workspace-b", "workspace-a"))
        assertTrue(isWorkspaceResultCurrent("workspace-b", "workspace-b"))
        assertFalse(isWorkspaceResultCurrent(null, "workspace-b"))
    }

    @Test
    fun sensitiveDetailsAreReleasedOutsideAccountFlows() {
        assertTrue(routeKeepsSensitiveDetails("AccountRoute"))
        assertTrue(routeKeepsSensitiveDetails("AccountEditRoute"))
        assertTrue(routeKeepsSensitiveDetails("SecretEditRoute"))
        assertFalse(routeKeepsSensitiveDetails("VaultRoute"))
        assertFalse(routeKeepsSensitiveDetails("GeneratorRoute"))
    }
}
