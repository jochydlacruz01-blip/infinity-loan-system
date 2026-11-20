package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CollectionPriorityTest {
    private val today = LocalDate.parse("2026-11-20")

    @Test fun prioritizesOlderDebtThenAmountAndKeepsCurrenciesSeparate() {
        val result = CollectionPriority.build(listOf(
            CollectionPriorityRecord("l1","c1","DOP",today.minusDays(5),500),
            CollectionPriorityRecord("l2","c2","USD",today.minusDays(65),100),
            CollectionPriorityRecord("l3","c3","DOP",today.minusDays(65),200),
            CollectionPriorityRecord("l4","c4","DOP",today.minusDays(32),900)
        ), today)
        assertEquals(listOf("l3","l2","l4","l1"), result.map { it.loanId })
        assertEquals(CollectionPriorityLevel.CRITICAL, result.first().level)
        assertEquals(CollectionPriorityLevel.HIGH, result[2].level)
        assertEquals(CollectionPriorityLevel.LOW, result.last().level)
    }

    @Test fun excludesCurrentFutureAndPaidInstallments() {
        val result = CollectionPriority.build(listOf(
            CollectionPriorityRecord("a","a","DOP",today,100),
            CollectionPriorityRecord("b","b","DOP",today.plusDays(1),100),
            CollectionPriorityRecord("c","c","DOP",today.minusDays(1),0)
        ),today)
        assertEquals(emptyList<CollectionPriorityItem>(), result)
    }

    @Test fun validatesCurrencyAndNegativeAmounts() {
        assertThrows(IllegalArgumentException::class.java) {
            CollectionPriority.build(listOf(CollectionPriorityRecord("a","b","GBP",today.minusDays(1),1)),today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollectionPriority.build(listOf(CollectionPriorityRecord("a","b","DOP",today.minusDays(1),-1)),today)
        }
    }
}
