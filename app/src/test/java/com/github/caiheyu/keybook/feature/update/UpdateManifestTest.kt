package com.github.caiheyu.keybook.feature.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateManifestTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    @Test
    fun acceptsTheExactEightFieldSchemaAndCaseInsensitiveHex() {
        val manifest = decodeAndValidateUpdateManifest(validManifest(), json)

        assertEquals(2, manifest.versionCode)
        assertEquals("A".repeat(64), manifest.apkSha256)
    }

    @Test
    fun rejectsDuplicateUnknownAndMissingFields() {
        val valid = validManifest()
        val invalid = listOf(
            valid.replaceFirst("{", "{\"schemaVersion\":1,"),
            valid.replaceFirst("{", "{\"unexpected\":true,"),
            valid.replace("\"releaseNotes\": \"更新说明\"", ""),
        )

        invalid.forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) {
                decodeAndValidateUpdateManifest(raw, json)
            }
        }
    }

    @Test
    fun rejectsInvalidTypesBoundsDigestsAndTimestamps() {
        val invalid = listOf(
            validManifest().replace("\"versionCode\": 2", "\"versionCode\": \"2\""),
            validManifest().replace("\"minSdk\": 26", "\"minSdk\": 25"),
            validManifest().replace("A".repeat(64), "g".repeat(64)),
            validManifest().replace("2026-09-17T00:00:00Z", "2026-09-17T08:00:00+08:00"),
            validManifest().replace("\"versionName\": \"1.1\"", "\"versionName\": \"\""),
        )

        invalid.forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) {
                decodeAndValidateUpdateManifest(raw, json)
            }
        }
    }

    private fun validManifest() = """
        {
          "schemaVersion": 1,
          "versionName": "1.1",
          "versionCode": 2,
          "minSdk": 26,
          "apkSha256": "${"A".repeat(64)}",
          "signerCertificateSha256": "${"b".repeat(64)}",
          "publishedAt": "2026-09-17T00:00:00Z",
          "releaseNotes": "更新说明"
        }
    """.trimIndent()
}
