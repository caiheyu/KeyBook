package com.github.caiheyu.keybook.feature.generator

import java.security.SecureRandom

data class PasswordRules(
    val length: Int = 20,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val symbolChars: String = DEFAULT_SYMBOLS,
    val excludeAmbiguous: Boolean = true,
    val guaranteeEachCategory: Boolean = true,
)

data class GeneratorPreset(
    val id: String,
    val name: String,
    val rules: PasswordRules,
)

object BuiltinGeneratorPresets {
    val standard = GeneratorPreset(
        id = "builtin.standard.v1",
        name = "标准强密码",
        rules = PasswordRules(),
    )

    val pin6 = GeneratorPreset(
        id = "builtin.pin6.v1",
        name = "数字 PIN",
        rules = PasswordRules(
            length = 6,
            lower = false,
            upper = false,
            digits = true,
            symbols = false,
            excludeAmbiguous = false,
            guaranteeEachCategory = false,
        ),
    )

    val all = listOf(standard, pin6)
}

class PasswordGenerator(
    private val random: SecureRandom = SecureRandom(),
) {
    fun generate(rules: PasswordRules): String {
        val categories = rules.categories()
        validate(rules, categories)
        val pool = categories.joinToString("")
        repeat(MAX_ATTEMPTS) {
            val candidate = buildString(rules.length) {
                repeat(rules.length) { append(pool[random.nextInt(pool.length)]) }
            }
            if (!rules.guaranteeEachCategory || categories.all { category ->
                    candidate.any(category::contains)
                }
            ) {
                return candidate
            }
        }
        throw IllegalStateException("无法在当前规则下生成满足类别保证的密码")
    }

    fun validate(rules: PasswordRules) = validate(rules, rules.categories())

    private fun validate(rules: PasswordRules, categories: List<String>) {
        require(rules.length in 4..128) { "密码长度须为 4–128" }
        require(categories.isNotEmpty()) { "至少开启一种字符类别" }
        if (rules.symbols) {
            require(rules.symbolChars.isNotEmpty()) { "符号集不能为空" }
            require(rules.symbolChars.all { it.code in 33..126 && !it.isLetterOrDigit() }) {
                "符号集只能包含 ASCII 可打印标点"
            }
            require(rules.symbolChars.toSet().size == rules.symbolChars.length) { "符号集不能包含重复字符" }
            require(rules.symbolChars.filtered(rules.excludeAmbiguous).isNotEmpty()) {
                "排除易混字符后符号集不能为空"
            }
        }
        if (rules.guaranteeEachCategory) {
            require(rules.length >= categories.size) { "密码长度不能小于已启用类别数" }
        }
    }

    private fun PasswordRules.categories(): List<String> = buildList {
        if (lower) add(LOWER.filtered(excludeAmbiguous))
        if (upper) add(UPPER.filtered(excludeAmbiguous))
        if (digits) add(DIGITS.filtered(excludeAmbiguous))
        if (symbols) add(symbolChars.filtered(excludeAmbiguous))
    }.filter { it.isNotEmpty() }

    private fun String.filtered(excludeAmbiguous: Boolean): String =
        if (excludeAmbiguous) filterNot(AMBIGUOUS::contains) else this

    companion object {
        private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
        private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DIGITS = "0123456789"
        private const val AMBIGUOUS = "0Oo1Il|"
        private const val MAX_ATTEMPTS = 4096
    }
}

const val DEFAULT_SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.?"
