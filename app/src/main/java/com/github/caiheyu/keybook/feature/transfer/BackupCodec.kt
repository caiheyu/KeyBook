package com.github.caiheyu.keybook.feature.transfer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.github.caiheyu.keybook.core.validation.InputValidation

@Singleton
class BackupCodec @Inject constructor(
    private val json: Json,
) {
    private val random = SecureRandom()

    fun encode(payload: BackupPayload, password: String?): ByteArray {
        val protectedPayload = payload.copy(
            protection = if (password == null) BackupProtection.NONE else BackupProtection.AES_256_GCM,
        )
        val plaintext = json.encodeToString(protectedPayload).toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_JSON_BYTES) { "备份内容超过 24 MiB" }
        if (password == null) return plaintext
        validatePassword(password)
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val nonce = ByteArray(NONCE_LENGTH).also(random::nextBytes)
        val header = createHeader(salt, nonce, plaintext.size.toLong() + GCM_TAG_LENGTH)
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)
            require(ciphertext.size.toLong() == plaintext.size.toLong() + GCM_TAG_LENGTH)
            header + ciphertext
        } finally {
            plaintext.fill(0)
            key.fill(0)
        }
    }

    fun decode(bytes: ByteArray, password: String?): BackupPayload {
        require(bytes.size <= MAX_FILE_BYTES) { "文件超过 32 MiB" }
        val encrypted = isEncrypted(bytes)
        val plaintext = if (encrypted) decrypt(bytes, password) else bytes.copyOf()
        try {
            require(plaintext.size <= MAX_JSON_BYTES) { "备份内容超过 24 MiB" }
            val raw = decodeUtf8Strict(plaintext)
            StrictJson.rejectDuplicateKeysAndInvalidSyntax(raw)
            val payload = runCatching { json.decodeFromString<BackupPayload>(raw) }
                .getOrElse { throw IllegalArgumentException("备份 JSON 结构无效", it) }
            val expected = if (encrypted) BackupProtection.AES_256_GCM else BackupProtection.NONE
            require(payload.protection == expected) { "备份加密标记不一致" }
            return payload
        } finally {
            plaintext.fill(0)
        }
    }

    fun isEncrypted(bytes: ByteArray): Boolean = bytes.size >= MAGIC.size &&
        bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun decrypt(bytes: ByteArray, password: String?): ByteArray {
        require(bytes.size >= HEADER_LENGTH + GCM_TAG_LENGTH) { "加密文件不完整" }
        requireNotNull(password) { "需要文件密码" }
        validatePassword(password)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "加密文件标志无效" }
        require(buffer.short.toInt() and 0xffff == FORMAT_VERSION) { "不支持的备份版本" }
        require(buffer.get().toInt() and 0xff == KDF_ID) { "不支持的密钥派生算法" }
        require(buffer.int == ITERATIONS) { "备份 KDF 参数无效" }
        require(buffer.get().toInt() and 0xff == SALT_LENGTH) { "备份盐长度无效" }
        val salt = ByteArray(SALT_LENGTH).also(buffer::get)
        require(buffer.get().toInt() and 0xff == CIPHER_ID) { "不支持的加密算法" }
        require(buffer.get().toInt() and 0xff == NONCE_LENGTH) { "备份 nonce 长度无效" }
        val nonce = ByteArray(NONCE_LENGTH).also(buffer::get)
        val ciphertextLength = buffer.long
        require(ciphertextLength == (bytes.size - HEADER_LENGTH).toLong()) { "加密文件长度无效" }
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(bytes, 0, HEADER_LENGTH)
            try {
                cipher.doFinal(bytes, HEADER_LENGTH, bytes.size - HEADER_LENGTH)
            } catch (error: AEADBadTagException) {
                throw IllegalArgumentException("文件密码错误或文件已损坏")
            }
        } finally {
            key.fill(0)
        }
    }

    private fun createHeader(salt: ByteArray, nonce: ByteArray, ciphertextLength: Long): ByteArray =
        ByteBuffer.allocate(HEADER_LENGTH).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC)
            putShort(FORMAT_VERSION.toShort())
            put(KDF_ID.toByte())
            putInt(ITERATIONS)
            put(SALT_LENGTH.toByte())
            put(salt)
            put(CIPHER_ID.toByte())
            put(NONCE_LENGTH.toByte())
            put(nonce)
            putLong(ciphertextLength)
        }.array()

    internal fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun validatePassword(password: String) {
        InputValidation.validateRaw(password, 12, 128, "文件密码")
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    companion object {
        const val MAX_FILE_BYTES = 32 * 1024 * 1024
        const val MAX_JSON_BYTES = 24 * 1024 * 1024
        const val FORMAT_VERSION = 2
        const val ITERATIONS = 600_000
        const val HEADER_LENGTH = 70
        private const val KDF_ID = 1
        private const val CIPHER_ID = 1
        private const val SALT_LENGTH = 32
        private const val NONCE_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val KEY_BITS = 256
        private val MAGIC = "KEYBOOK1".toByteArray(Charsets.US_ASCII)
    }
}
