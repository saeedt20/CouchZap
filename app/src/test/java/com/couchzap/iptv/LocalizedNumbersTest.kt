package com.couchzap.iptv

import com.couchzap.iptv.util.LocalizedNumbers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Item 9: Persian/Arabic digit localization. Display only; identities stay ASCII. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class LocalizedNumbersTest {
    @Test fun `fa uses Persian digits`() {
        assertEquals("۲۱۹۲", LocalizedNumbers.localize("2192", "fa"))
        assertEquals("۰۱۲۳۴۵۶۷۸۹", LocalizedNumbers.localize("0123456789", "fa"))
    }
    @Test fun `ar uses Arabic digits`() {
        assertEquals("٢١٩٢", LocalizedNumbers.localize("2192", "ar"))
        assertEquals("٠١٢٣٤٥٦٧٨٩", LocalizedNumbers.localize("0123456789", "ar"))
    }
    @Test fun `other locales keep ASCII`() {
        listOf("en", "es", "hi", "th", null).forEach { lang ->
            assertEquals("2192", LocalizedNumbers.localize("2192", lang))
        }
    }
    @Test fun `non-digit characters pass through`() {
        assertEquals("CH ۲۱۹۲", LocalizedNumbers.localize("CH 2192", "fa"))
        assertEquals("a۱b", LocalizedNumbers.localize("a1b", "fa"))
        // Callers must not apply localize to names/URLs/versions; localize itself only maps 0-9.
        assertEquals("۱۲۳", LocalizedNumbers.localize("123", "fa"))
    }
    @Test fun `channelNumber pads to three digits and localizes`() {
        assertEquals("۰۰۳", LocalizedNumbers.channelNumber(3, "fa"))
        assertEquals("٢١٩٢", LocalizedNumbers.channelNumber(2192, "ar"))
        assertEquals("2192", LocalizedNumbers.channelNumber(2192, "en"))
        // Long source numbers are not truncated.
        assertEquals(LocalizedNumbers.localize("12345", "fa"), LocalizedNumbers.channelNumber(12345, "fa"))
    }
    @Test fun `normalizeForComparison maps localized digits to ASCII`() {
        assertEquals("2192", LocalizedNumbers.normalizeForComparison("۲۱۹۲"))
        assertEquals("2192", LocalizedNumbers.normalizeForComparison("٢١٩٢"))
        assertEquals("2192", LocalizedNumbers.normalizeForComparison("2192"))
        assertEquals("a2192b", LocalizedNumbers.normalizeForComparison("a۲۱۹۲b"))
        assertEquals("", LocalizedNumbers.normalizeForComparison(""))
    }
    @Test fun `normalizeForComparison is comparison-only and preserves original`() {
        val typed = "۲۱۹۲"
        val normalized = LocalizedNumbers.normalizeForComparison(typed)
        assertEquals("۲۱۹۲", typed) // original untouched
        assertEquals("2192", normalized)
        assertTrue("2192".startsWith(normalized))
    }
}
