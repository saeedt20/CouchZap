package com.couchzap.iptv.util

/**
 * Item 9: Persian/Arabic digit localization for channel UI.
 *
 * Uses the selected APP language (not the system locale). Only display text is
 * localized; Channel.number, source order, stable IDs, saved data and numeric
 * key decoding stay ASCII/numeric. Non-digit characters (names, URLs, versions)
 * are never transformed.
 */
object LocalizedNumbers {
    /** U+06F0–U+06F9 */
    private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    /** U+0660–U+0669 */
    private const val AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    fun digitsFor(language: String?): String? = when (language) {
        "fa" -> FA_DIGITS
        "ar" -> AR_DIGITS
        else -> null
    }

    /** Localizes ASCII digits in [text]; all other characters pass through untouched. */
    fun localize(text: String, language: String?): String {
        val digits = digitsFor(language) ?: return text
        if (text.none { it in '0'..'9' }) return text
        return buildString(text.length) {
            for (c in text) append(if (c in '0'..'9') digits[c - '0'] else c)
        }
    }

    fun localize(number: Int, language: String?): String = localize(number.toString(), language)

    /** Minimum-three-digit channel formatting, localized for the app language. */
    fun channelNumber(number: Int, language: String?): String =
        localize("%03d".format(java.util.Locale.US, number), language)

    /**
     * Normalizes Persian/Arabic decimal digits to ASCII for comparison only.
     * The typed text, query and caret are preserved by callers.
     */
    fun normalizeForComparison(text: String): String {
        var out: StringBuilder? = null
        for (i in text.indices) {
            val c = text[i]
            val n = when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
            if (n != c) {
                if (out == null) { out = StringBuilder(text.length); out.append(text, 0, i) }
                out.append(n)
            } else out?.append(c)
        }
        return out?.toString() ?: text
    }
}
