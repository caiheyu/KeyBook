package com.github.caiheyu.keybook.feature.transfer

import java.util.UUID
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec(
        Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
        },
    )

    @Test
    fun plainAndEncryptedV2RoundTrip() {
        val payload = samplePayload()
        val plain = codec.encode(payload, null)
        assertEquals(BackupProtection.NONE, codec.decode(plain, null).protection)

        val encrypted = codec.encode(payload, " 文件密码🔐123456 ")
        assertTrue(codec.isEncrypted(encrypted))
        assertEquals(2, ((encrypted[8].toInt() and 0xff) shl 8) or (encrypted[9].toInt() and 0xff))
        assertEquals(BackupProtection.AES_256_GCM, codec.decode(encrypted, " 文件密码🔐123456 ").protection)
    }

    @Test
    fun rejectsWrongPasswordAndAuthenticatedCiphertextChanges() {
        val encrypted = codec.encode(samplePayload(), "正确的文件密码123456")
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encrypted, "错误的文件密码123456")
        }
        encrypted[BackupCodec.HEADER_LENGTH + 3] = (encrypted[BackupCodec.HEADER_LENGTH + 3].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encrypted, "正确的文件密码123456")
        }
    }

    @Test
    fun rejectsTruncatedEncryptedFilesAndTamperedKdfParameters() {
        val password = "正确的文件密码123456"
        val encrypted = codec.encode(samplePayload(), password)

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(encrypted.copyOf(BackupCodec.HEADER_LENGTH + 15), password)
        }

        val tamperedIterations = encrypted.copyOf()
        tamperedIterations[14] = (tamperedIterations[14].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(tamperedIterations, password)
        }
    }

    @Test
    fun rejectsDuplicateAndUnknownJsonFields() {
        val valid = codec.encode(samplePayload(), null).toString(Charsets.UTF_8)
        val duplicate = valid.replaceFirst("{", "{\"schemaVersion\":2,")
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(duplicate.toByteArray(), null)
        }
        val unknown = valid.replaceFirst("{", "{\"unknown\":true,")
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(unknown.toByteArray(), null)
        }
    }

    @Test
    fun rejectsDuplicateAndUnknownNestedJsonFields() {
        val valid = codec.encode(samplePayload(), null).toString(Charsets.UTF_8)
        val duplicate = valid.replaceFirst(
            "\"kind\":\"BUILTIN\"",
            "\"kind\":\"BUILTIN\",\"kind\":\"CUSTOM\"",
        )
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(duplicate.toByteArray(), null)
        }

        val unknown = valid.replaceFirst("\"data\":{", "\"data\":{\"unknown\":true,")
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(unknown.toByteArray(), null)
        }
    }

    @Test
    fun pbkdf2MatchesUtf8CrossPlatformVector() {
        val salt = ByteArray(32) { it.toByte() }
        val actual = codec.deriveKey(" 密码🔐 ", salt)
        assertArrayEquals(
            "40f93f993807a8efadc35c4fee51487eb811f098035230cd06030f05957d24fb".hexToBytes(),
            actual,
        )
    }

    private fun samplePayload() = BackupPayload(
        schemaVersion = 2,
        exportId = UUID.fromString("00000000-0000-4000-8000-000000000001").toString(),
        createdAt = "2026-09-10T00:00:00Z",
        scope = BackupScope.WORKSPACES,
        protection = BackupProtection.NONE,
        workspaces = listOf(
            BackupWorkspace(
                id = UUID.fromString("00000000-0000-4000-8000-000000000002").toString(),
                name = "个人",
                data = BackupWorkspaceData(
                    companies = emptyList(),
                    apps = emptyList(),
                    accounts = emptyList(),
                    secretItems = emptyList(),
                    generatorPresets = emptyList(),
                    defaultGenerator = BackupGeneratorSelection("BUILTIN", "builtin.standard.v1"),
                    catalogPresets = emptyList(),
                    builtinCatalogOverrides = emptyList(),
                    iconAssets = emptyList(),
                ),
            ),
        ),
    )

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
