package com.github.caiheyu.keybook.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultCrypto @Inject constructor() {
    private val random = SecureRandom()

    fun createVaultKey(): ByteArray = ByteArray(KEY_SIZE_BYTES).also(random::nextBytes)

    fun wrapVaultKey(workspaceId: String, vaultKey: ByteArray): CryptoBox =
        encrypt(
            key = getOrCreateDeviceKey(),
            plaintext = vaultKey,
            aad = RecordAad.encode(TYPE_VAULT_KEY, workspaceId, workspaceId, null),
        )

    fun unwrapVaultKey(workspaceId: String, box: CryptoBox): ByteArray =
        decrypt(
            key = getDeviceKeyOrThrow(),
            box = box,
            aad = RecordAad.encode(TYPE_VAULT_KEY, workspaceId, workspaceId, null),
        )

    fun encryptRecord(vaultKey: ByteArray, plaintext: ByteArray, aad: ByteArray): CryptoBox =
        encrypt(SecretKeySpec(vaultKey, KeyProperties.KEY_ALGORITHM_AES), plaintext, aad)

    fun decryptRecord(vaultKey: ByteArray, box: CryptoBox, aad: ByteArray): ByteArray =
        decrypt(SecretKeySpec(vaultKey, KeyProperties.KEY_ALGORITHM_AES), box, aad)

    private fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray): CryptoBox {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        return CryptoBox(
            nonce = cipher.iv.copyOf(),
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    private fun decrypt(key: SecretKey, box: CryptoBox, aad: ByteArray): ByteArray {
        require(box.nonce.size == NONCE_SIZE_BYTES) { "无效的加密随机数" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, box.nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(box.ciphertext)
    }

    @Synchronized
    private fun getOrCreateDeviceKey(): SecretKey {
        getDeviceKey()?.let { return it }
        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    DEVICE_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKey()
        }
    }

    private fun getDeviceKeyOrThrow(): SecretKey = getDeviceKey()
        ?: throw DeviceKeyUnavailableException()

    private fun getDeviceKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(DEVICE_KEY_ALIAS, null) as? SecretKey
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEVICE_KEY_ALIAS = "com.github.caiheyu.keybook.device.wrap.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TYPE_VAULT_KEY = "vault-key"
        private const val KEY_SIZE_BITS = 256
        private const val KEY_SIZE_BYTES = KEY_SIZE_BITS / 8
        private const val NONCE_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
    }
}

class DeviceKeyUnavailableException : IllegalStateException(
    "设备加密密钥不可用。现有数据已保留，密钥册不会创建空库覆盖它。",
)
