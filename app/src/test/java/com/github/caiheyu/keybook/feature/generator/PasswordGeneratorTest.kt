package com.github.caiheyu.keybook.feature.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    private val generator = PasswordGenerator()

    @Test
    fun standardPresetHasEveryEnabledCategory() {
        repeat(100) {
            val password = generator.generate(BuiltinGeneratorPresets.standard.rules)
            assertEquals(20, password.length)
            assertTrue(password.any(Char::isLowerCase))
            assertTrue(password.any(Char::isUpperCase))
            assertTrue(password.any(Char::isDigit))
            assertTrue(password.any(DEFAULT_SYMBOLS::contains))
            assertFalse(password.any("0Oo1Il|"::contains))
        }
    }

    @Test
    fun pinPresetProducesSixDigits() {
        repeat(100) {
            val password = generator.generate(BuiltinGeneratorPresets.pin6.rules)
            assertEquals(6, password.length)
            assertTrue(password.all(Char::isDigit))
        }
    }

    @Test
    fun supportsMinimumAndMaximumPasswordLengths() {
        val minimum = generator.generate(
            PasswordRules(length = 4, upper = false, digits = false, symbols = false),
        )
        val maximum = generator.generate(
            PasswordRules(length = 128, lower = false, upper = false, symbols = false),
        )

        assertEquals(4, minimum.length)
        assertTrue(minimum.all(Char::isLowerCase))
        assertEquals(128, maximum.length)
        assertTrue(maximum.all(Char::isDigit))
    }

    @Test
    fun minimumLengthCanGuaranteeEveryEnabledCategory() {
        val password = generator.generate(
            PasswordRules(length = 4, symbolChars = "!", excludeAmbiguous = false),
        )

        assertTrue(password.any(Char::isLowerCase))
        assertTrue(password.any(Char::isUpperCase))
        assertTrue(password.any(Char::isDigit))
        assertTrue(password.contains('!'))
    }

    @Test
    fun rejectsEmptyAndDuplicateSymbolSets() {
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(PasswordRules(symbolChars = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(PasswordRules(symbolChars = "!!"))
        }
    }

    @Test
    fun rejectsGuaranteeWhenLengthIsTooShort() {
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(PasswordRules(length = 3))
        }
    }

    @Test
    fun rejectsRulesWithoutAnyEnabledCategory() {
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(
                PasswordRules(lower = false, upper = false, digits = false, symbols = false),
            )
        }
    }

    @Test
    fun rejectsSymbolCategoryEmptyAfterAmbiguousCharactersAreExcluded() {
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(
                PasswordRules(
                    lower = false,
                    upper = false,
                    digits = false,
                    symbolChars = "|",
                    excludeAmbiguous = true,
                ),
            )
        }
    }
}
