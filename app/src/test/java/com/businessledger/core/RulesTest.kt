package com.businessledger.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class RulesTest {
    private fun d(s: String) = BigDecimal(s)
    @Test fun discountAndTaxUseDecimalRounding() {
        val result = Rules.line(d("2.5"), d("19.99"), d("4.98"), d("17"), 2)
        assertEquals(d("45.00"), result.net)
        assertEquals(d("7.65"), result.tax)
        assertEquals(d("52.65"), result.total)
    }
    @Test fun paymentCannotOverallocate() {
        assertEquals(d("25.00"), Rules.allocation(d("100.00"), d("75.00")))
        assertThrows(IllegalArgumentException::class.java) { Rules.allocation(d("10"), d("11")) }
    }
    @Test fun stockRejectsNegativeUnlessEnabled() {
        assertThrows(IllegalArgumentException::class.java) { Rules.stock(d("1"), d("-2"), false) }
        assertEquals(d("-1"), Rules.stock(d("1"), d("-2"), true))
    }
    @Test fun offlineNumberCollisionRequiresAttention() {
        assertEquals(NumberResolution.NeedsAttention("INV-7", "other"), resolveNumber("INV-7", "mine", "other"))
        assertEquals(NumberResolution.Accepted("INV-7"), resolveNumber("INV-7", "mine", "mine"))
    }
}
