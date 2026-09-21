package com.github.caiheyu.keybook.core.validation

import java.net.URI

object InputValidation {
    fun normalizeName(value: String, maxCodePoints: Int = 100): String {
        require(isWellFormedUnicode(value)) { "包含无效字符" }
        val normalized = trimUnicodeWhitespace(value)
        val length = normalized.codePointCount(0, normalized.length)
        require(length in 1..maxCodePoints) { "名称须为 1–$maxCodePoints 个字符" }
        return normalized
    }

    fun validateRaw(value: String, minCodePoints: Int, maxCodePoints: Int, label: String) {
        require(isWellFormedUnicode(value)) { "$label 包含无效字符" }
        val length = value.codePointCount(0, value.length)
        require(length in minCodePoints..maxCodePoints) {
            "$label 须为 $minCodePoints–$maxCodePoints 个字符"
        }
    }

    fun normalizeNote(value: String): String {
        require(isWellFormedUnicode(value)) { "备注包含无效字符" }
        require(value.codePointCount(0, value.length) <= 4000) { "备注最多 4000 个字符" }
        return value
    }

    fun validateWebsite(value: String): String {
        if (value.isEmpty()) return value
        require(value.length <= 2048) { "官网地址最多 2048 个字符" }
        require(isWellFormedUnicode(value)) { "官网地址包含无效字符" }
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw IllegalArgumentException("官网地址格式无效")
        require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            "官网地址仅支持 HTTP 或 HTTPS"
        }
        require(uri.rawUserInfo == null) { "官网地址不能包含用户名或密码" }
        require(!uri.host.isNullOrBlank()) { "官网地址缺少有效主机名" }
        require(uri.port == -1 || uri.port in 1..65535) { "官网地址端口无效" }
        return value
    }

    fun isWellFormedUnicode(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val current = value[index]
            when {
                Character.isHighSurrogate(current) -> {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) {
                        return false
                    }
                    index += 2
                }
                Character.isLowSurrogate(current) -> return false
                else -> index++
            }
        }
        return true
    }

    private fun trimUnicodeWhitespace(value: String): String {
        var start = 0
        var end = value.length
        while (start < end) {
            val codePoint = value.codePointAt(start)
            if (!codePoint.isUnicodeSpace()) break
            start += Character.charCount(codePoint)
        }
        while (end > start) {
            val codePoint = value.codePointBefore(end)
            if (!codePoint.isUnicodeSpace()) break
            end -= Character.charCount(codePoint)
        }
        return value.substring(start, end)
    }

    private fun Int.isUnicodeSpace(): Boolean =
        Character.isWhitespace(this) || Character.isSpaceChar(this)
}
