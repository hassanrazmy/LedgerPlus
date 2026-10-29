package com.businessledger.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Store decimal strings, never binary floating point values. */
object Rules {
    fun decimal(raw: String): BigDecimal = raw.trim().toBigDecimal().also { require(it.scale() <= 6) }
    fun money(raw: String, scale: Int): BigDecimal = decimal(raw).setScale(scale, RoundingMode.HALF_UP)
    fun line(quantity: BigDecimal, unitPrice: BigDecimal, discount: BigDecimal, taxPercent: BigDecimal, scale: Int): LineTotal {
        require(quantity > BigDecimal.ZERO && unitPrice >= BigDecimal.ZERO)
        require(discount >= BigDecimal.ZERO && taxPercent >= BigDecimal.ZERO && taxPercent <= BigDecimal("100"))
        val gross = quantity.multiply(unitPrice)
        require(discount <= gross) { "Discount exceeds line amount" }
        val net = gross.subtract(discount).setScale(scale, RoundingMode.HALF_UP)
        val tax = net.multiply(taxPercent).divide(BigDecimal("100"), scale, RoundingMode.HALF_UP)
        return LineTotal(net, tax, net.add(tax))
    }
    fun allocation(openBalance: BigDecimal, amount: BigDecimal): BigDecimal {
        require(amount > BigDecimal.ZERO && amount <= openBalance) { "Payment exceeds open balance" }
        return openBalance.subtract(amount)
    }
    fun stock(onHand: BigDecimal, delta: BigDecimal, allowNegative: Boolean): BigDecimal {
        val result = onHand.add(delta)
        require(allowNegative || result >= BigDecimal.ZERO) { "Insufficient stock" }
        return result
    }
}

data class LineTotal(val net: BigDecimal, val tax: BigDecimal, val total: BigDecimal)

/** A sync server must never pick a winner silently when two offline proposals collide. */
sealed interface NumberResolution {
    data class Accepted(val number: String) : NumberResolution
    data class NeedsAttention(val proposed: String, val existingDocumentId: String) : NumberResolution
}
fun resolveNumber(proposed: String, currentDocumentId: String, existingDocumentId: String?): NumberResolution =
    if (existingDocumentId == null || existingDocumentId == currentDocumentId) NumberResolution.Accepted(proposed)
    else NumberResolution.NeedsAttention(proposed, existingDocumentId)
