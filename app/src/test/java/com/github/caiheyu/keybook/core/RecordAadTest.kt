package com.github.caiheyu.keybook.core

import com.github.caiheyu.keybook.core.crypto.RecordAad
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecordAadTest {
    @Test
    fun encodingIsDeterministic() {
        val first = RecordAad.encode("account", "workspace", "record", "parent")
        val second = RecordAad.encode("account", "workspace", "record", "parent")

        assertArrayEquals(first, second)
    }

    @Test
    fun parentAndWorkspaceAreBound() {
        val original = RecordAad.encode("account", "workspace", "record", "parent")

        assertFalse(original.contentEquals(RecordAad.encode("account", "other", "record", "parent")))
        assertFalse(original.contentEquals(RecordAad.encode("account", "workspace", "record", "other")))
    }
}
