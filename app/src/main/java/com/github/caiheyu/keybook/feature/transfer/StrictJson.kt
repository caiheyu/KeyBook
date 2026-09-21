package com.github.caiheyu.keybook.feature.transfer

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal object StrictJson {
    private val json = Json

    fun rejectDuplicateKeysAndInvalidSyntax(raw: String) {
        Parser(raw).parse()
    }

    private class Parser(private val raw: String) {
        private var at = 0

        fun parse() {
            value(0)
            whitespace()
            require(at == raw.length) { "JSON 包含尾随数据" }
        }

        private fun value(depth: Int) {
            require(depth <= MAX_DEPTH) { "JSON 嵌套过深" }
            whitespace()
            require(at < raw.length) { "JSON 不完整" }
            when (raw[at]) {
                '"' -> string()
                '{' -> objectValue(depth)
                '[' -> arrayValue(depth)
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                else -> number()
            }
        }

        private fun objectValue(depth: Int) {
            at++
            whitespace()
            if (consume('}')) return
            val keys = mutableSetOf<String>()
            while (true) {
                whitespace()
                require(at < raw.length && raw[at] == '"') { "JSON 对象无效" }
                val key = string()
                require(keys.add(key)) { "JSON 包含重复字段：$key" }
                whitespace()
                require(consume(':')) { "JSON 对象无效" }
                value(depth + 1)
                whitespace()
                if (consume('}')) return
                require(consume(',')) { "JSON 对象无效" }
            }
        }

        private fun arrayValue(depth: Int) {
            at++
            whitespace()
            if (consume(']')) return
            while (true) {
                value(depth + 1)
                whitespace()
                if (consume(']')) return
                require(consume(',')) { "JSON 数组无效" }
            }
        }

        private fun string(): String {
            val start = at
            require(raw[at++] == '"')
            while (at < raw.length) {
                when (raw[at++]) {
                    '"' -> {
                        val token = raw.substring(start, at)
                        return runCatching { json.decodeFromString<String>(token) }
                            .getOrElse { throw IllegalArgumentException("JSON 字符串无效") }
                    }
                    '\\' -> {
                        require(at < raw.length) { "JSON 字符串不完整" }
                        if (raw[at] == 'u') {
                            at++
                            require(at + 4 <= raw.length && raw.substring(at, at + 4).all { it.isHexDigit() }) {
                                "JSON Unicode 转义无效"
                            }
                            at += 4
                        } else {
                            require(raw[at] in charArrayOf('"', '\\', '/', 'b', 'f', 'n', 'r', 't')) {
                                "JSON 转义无效"
                            }
                            at++
                        }
                    }
                    in '\u0000'..'\u001f' -> throw IllegalArgumentException("JSON 字符串包含控制字符")
                }
            }
            throw IllegalArgumentException("JSON 字符串不完整")
        }

        private fun number() {
            val match = NUMBER.find(raw, at)
            require(match != null && match.range.first == at) { "JSON 值无效" }
            at = match.range.last + 1
        }

        private fun literal(value: String) {
            require(raw.regionMatches(at, value, 0, value.length)) { "JSON 值无效" }
            at += value.length
        }

        private fun whitespace() {
            while (at < raw.length && raw[at] in charArrayOf(' ', '\t', '\r', '\n')) at++
        }

        private fun consume(char: Char): Boolean = if (at < raw.length && raw[at] == char) {
            at++
            true
        } else false
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private const val MAX_DEPTH = 64
    private val NUMBER = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
}
