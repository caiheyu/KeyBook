package com.github.caiheyu.keybook.core

import com.github.caiheyu.keybook.core.validation.InputValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidationTest {
    @Test
    fun trimsUnicodeWhitespaceWithoutNormalizingContent() {
        assertEquals("名称", InputValidation.normalizeName("\u3000名称\u00A0"))
    }

    @Test
    fun countsUnicodeCodePoints() {
        assertEquals("😀", InputValidation.normalizeName("😀", 1))
        assertThrows(IllegalArgumentException::class.java) {
            InputValidation.normalizeName("😀a", 1)
        }
    }

    @Test
    fun rejectsUnpairedSurrogates() {
        assertFalse(InputValidation.isWellFormedUnicode("\uD800"))
        assertTrue(InputValidation.isWellFormedUnicode("😀"))
    }

    @Test
    fun acceptsCustomHttpPortAndRejectsUserInfo() {
        assertEquals(
            "http://101.133.135.193:20080/",
            InputValidation.validateWebsite("http://101.133.135.193:20080/"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            InputValidation.validateWebsite("https://name:secret@example.com/")
        }
    }
}
