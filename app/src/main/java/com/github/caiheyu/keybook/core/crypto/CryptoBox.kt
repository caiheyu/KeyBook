package com.github.caiheyu.keybook.core.crypto

data class CryptoBox(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)
