package com.github.caiheyu.keybook.core.crypto

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object RecordAad {
    const val FORMAT_VERSION = 1
    const val KEY_VERSION = 1

    fun encode(
        type: String,
        workspaceId: String,
        recordId: String,
        parentId: String?,
        formatVersion: Int = FORMAT_VERSION,
        keyVersion: Int = KEY_VERSION,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(formatVersion)
            data.writeInt(keyVersion)
            data.writeLengthPrefixed(type)
            data.writeLengthPrefixed(workspaceId)
            data.writeLengthPrefixed(recordId)
            data.writeBoolean(parentId != null)
            if (parentId != null) data.writeLengthPrefixed(parentId)
        }
        return output.toByteArray()
    }

    private fun DataOutputStream.writeLengthPrefixed(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
