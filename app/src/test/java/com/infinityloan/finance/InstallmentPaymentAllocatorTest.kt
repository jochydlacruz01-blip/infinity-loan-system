package com.infinityloan.finance

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallmentPaymentAllocatorTest {

    @Test
    fun allocatesOldestInstallmentAndRespectsPriorityWithinIt() {
        val result = InstallmentPaymentAllocator.allocate(
            paymentMinor = 900L,
            installments = listOf(
                InstallmentPaymentAllocator.DueInstallment(
                    number = 2,
                    dueDate = LocalDate.parse("2026-02-01"),
                    lateFeeDueMinor = 50L,
                    chargesDueMinor = 50L,
                    interestDueMinor = 100L,
                    principalDueMinor = 300L
                ),
                InstallmentPaymentAllocator.DueInstallment(
                    number = 1,
                    dueDate = LocalDate.parse("2026-01-01"),
                    lateFeeDueMinor = 100L,
                    chargesDueMinor = 100L,
                    interestDueMinor = 200L,
                    principalDueMinor = 300L
                )
            )
        )

        assertEquals(900L, result.totalAllocatedMinor)
        assertEquals(0L, result.unappliedMinor)
        assertEquals(
            InstallmentPaymentAllocator.InstallmentAllocation(
                installmentNumber = 1,
                lateFeePaidMinor = 100L,
                chargesPaidMinor = 100L,
                interestPaidMinor = 200L,
                principalPaidMinor = 300L
            ),
            result.allocations[0]
        )
        assertEquals(
            InstallmentPaymentAllocator.InstallmentAllocation(
                installmentNumber = 2,
                lateFeePaidMinor = 50L,
                chargesPaidMinor = 50L,
                interestPaidMinor = 100L,
                principalPaidMinor = 0L
            ),
            result.allocations[1]
        )
    }

    @Test
    fun returnsExcessAsUnappliedWithoutChangingInstallmentAmounts() {
        val result = InstallmentPaymentAllocator.allocate(
            paymentMinor = 1_000L,
            installments = listOf(
                InstallmentPaymentAllocator.DueInstallment(
                    number = 1,
                    dueDate = LocalDate.parse("2026-01-01"),
                    interestDueMinor = 100L,
                    principalDueMinor = 300L
                )
            )
        )

        assertEquals(400L, result.totalAllocatedMinor)
        assertEquals(600L, result.unappliedMinor)
    }

    @Test
    fun rejectsNegativeDueAmountsAndDuplicateInstallmentNumbers() {
        val date = LocalDate.parse("2026-01-01")
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentPaymentAllocator.allocate(
                100L,
                listOf(
                    InstallmentPaymentAllocator.DueInstallment(
                        1, date, interestDueMinor = -1L, principalDueMinor = 0L
                    )
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentPaymentAllocator.allocate(
                100L,
                listOf(
                    InstallmentPaymentAllocator.DueInstallment(
                        1, date, interestDueMinor = 0L, principalDueMinor = 50L
                    ),
                    InstallmentPaymentAllocator.DueInstallment(
                        1, date.plusDays(7), interestDueMinor = 0L, principalDueMinor = 50L
                    )
                )
            )
        }
    }

    @Test
    fun handlesMaximumLongPaymentWithoutOverflowingAllocationTotals() {
        val result = InstallmentPaymentAllocator.allocate(
            paymentMinor = Long.MAX_VALUE,
            installments = listOf(
                InstallmentPaymentAllocator.DueInstallment(
                    number = 1,
                    dueDate = LocalDate.parse("2026-01-01"),
                    interestDueMinor = 0L,
                    principalDueMinor = Long.MAX_VALUE
                ),
                InstallmentPaymentAllocator.DueInstallment(
                    number = 2,
                    dueDate = LocalDate.parse("2026-02-01"),
                    interestDueMinor = 0L,
                    principalDueMinor = Long.MAX_VALUE
                )
            )
        )

        assertEquals(Long.MAX_VALUE, result.totalAllocatedMinor)
        assertEquals(0L, result.unappliedMinor)
        assertEquals(Long.MAX_VALUE, result.allocations.single().totalPaidMinor)
    }

    @Test
    fun rejectsZeroOrNegativePayments() {
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentPaymentAllocator.allocate(0L, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            InstallmentPaymentAllocator.allocate(-1L, emptyList())
        }
    }

    @Test
    fun sameDueDateUsesInstallmentNumberAsDeterministicTieBreaker() {
        val date = LocalDate.parse("2026-03-15")
        val result = InstallmentPaymentAllocator.allocate(
            paymentMinor = 250L,
            installments = listOf(
                InstallmentPaymentAllocator.DueInstallment(3, date, interestDueMinor = 0L, principalDueMinor = 0L),
                InstallmentPaymentAllocator.DueInstallment(1, date, interestDueMinor = 0L, principalDueMinor = 100L),
                InstallmentPaymentAllocator.DueInstallment(2, date, interestDueMinor = 0L, principalDueMinor = 100L)
            )
        )
        assertEquals(listOf(1, 2), result.allocations.map { it.installmentNumber })
        assertEquals(50L, result.unappliedMinor)
    }

    @Test
    fun skipsInstallmentsWithNoOutstandingAmounts() {
        val result = InstallmentPaymentAllocator.allocate(
            paymentMinor = 50L,
            installments = listOf(
                InstallmentPaymentAllocator.DueInstallment(1, LocalDate.parse("2026-01-01"), interestDueMinor = 0L, principalDueMinor = 0L),
                InstallmentPaymentAllocator.DueInstallment(2, LocalDate.parse("2026-01-02"), interestDueMinor = 10L, principalDueMinor = 40L)
            )
        )
        assertEquals(1, result.allocations.size)
        assertEquals(2, result.allocations.single().installmentNumber)
        assertEquals(50L, result.totalAllocatedMinor)
        assertEquals(0L, result.unappliedMinor)
    }
    @Test
    fun conservesPaymentAcrossDeterministicManyInstallmentCases() {
        repeat(120) { seed ->
            val installments = (1..8).map { n ->
                InstallmentPaymentAllocator.DueInstallment(
                    number = n,
                    dueDate = LocalDate.parse("2026-01-01").plusDays(((seed * 13 + n * 7) % 31).toLong()),
                    lateFeeDueMinor = ((seed + n * 3) % 17).toLong(),
                    chargesDueMinor = ((seed * 2 + n) % 23).toLong(),
                    interestDueMinor = ((seed * 5 + n * 2) % 41).toLong(),
                    principalDueMinor = ((seed * 7 + n * 11) % 101).toLong()
                )
            }
            val payment = (seed * 19 + 1).toLong()
            val result = InstallmentPaymentAllocator.allocate(payment, installments)
            assertEquals(payment, Math.addExact(result.totalAllocatedMinor, result.unappliedMinor))
            assertTrue(result.unappliedMinor >= 0L)
            assertTrue(result.allocations.all { allocation ->
                allocation.lateFeePaidMinor >= 0L &&
                    allocation.chargesPaidMinor >= 0L &&
                    allocation.interestPaidMinor >= 0L &&
                    allocation.principalPaidMinor >= 0L
            })
        }
    }

}
