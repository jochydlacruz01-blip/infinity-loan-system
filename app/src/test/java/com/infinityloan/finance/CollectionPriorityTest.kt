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

    @Test fun overdueThresholdsAreExactAndZeroDaysNeverOverdue() {
        val result = CollectionPriority.build((1L..61L).map { days ->
            CollectionPriorityRecord("loan$days","client$days","DOP",today.minusDays(days),days)
        } + CollectionPriorityRecord("today","today","DOP",today,99), today)
        assertEquals(61, result.size)
        assertEquals(CollectionPriorityLevel.CRITICAL, result.first { it.daysOverdue == 61L }.level)
        assertEquals(CollectionPriorityLevel.HIGH, result.first { it.daysOverdue == 31L }.level)
        assertEquals(CollectionPriorityLevel.MEDIUM, result.first { it.daysOverdue == 8L }.level)
        assertEquals(CollectionPriorityLevel.LOW, result.first { it.daysOverdue == 1L }.level)
    }

    @Test fun deterministicTieBreakersAndIdentifierValidation() {
        val same = today.minusDays(10)
        val rows = listOf(
            CollectionPriorityRecord("z","c","DOP",same,100),
            CollectionPriorityRecord("a","c","DOP",same,100),
            CollectionPriorityRecord("b","b","USD",same,100)
        )
        assertEquals(listOf("b","a","z"), CollectionPriority.build(rows,today).map { it.loanId })
        assertThrows(IllegalArgumentException::class.java) {
            CollectionPriority.build(listOf(CollectionPriorityRecord(" ","c","DOP",same,1)),today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollectionPriority.build(listOf(CollectionPriorityRecord("l"," ","DOP",same,1)),today)
        }
    }

}
