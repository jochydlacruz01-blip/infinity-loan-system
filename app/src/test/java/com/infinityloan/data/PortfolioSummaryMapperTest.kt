package com.infinityloan.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortfolioSummaryMapperTest {
    @Test
    fun loanMapsPersistedCurrencyStatusAndOutstandingBalance() {
        val loan = LoanEntity(
            id = "loan-1",
            clientId = "client-1",
            principalMinor = 10_000L,
            balanceMinor = 6_500L,
            currency = "DOP",
            periodicRate = "0.05",
            periods = 4,
            method = "FLAT"
        )

        val mapped = PortfolioSummaryMapper.toRecord(loan)
        assertEquals("DOP", mapped.currency)
        assertEquals("ACTIVE", mapped.status)
        assertEquals(6_500L, mapped.outstandingPrincipalMinor)
    }

    @Test
    fun installmentMapsUnpaidScheduledAmountAndLoanCurrency() {
        val installment = LoanInstallmentEntity(
            loanId = "loan-1",
            number = 1,
            dueDate = "2026-11-01",
            principalMinor = 5_000L,
            interestMinor = 500L,
            totalMinor = 5_500L,
            remainingPrincipalMinor = 5_000L,
            paidInterestMinor = 500L,
            paidPrincipalMinor = 2_000L
        )

        val mapped = PortfolioSummaryMapper.toRecord(installment, "USD")
        assertEquals("USD", mapped.currency)
        assertEquals(LocalDate.parse("2026-11-01"), mapped.dueDate)
        assertEquals(3_000L, mapped.unpaidScheduledMinor)
    }

    @Test
    fun installmentRejectsPaidAmountGreaterThanScheduledTotal() {
        val installment = LoanInstallmentEntity(
            loanId = "loan-1",
            number = 1,
            dueDate = "2026-11-01",
            principalMinor = 5_000L,
            interestMinor = 500L,
            totalMinor = 5_500L,
            remainingPrincipalMinor = 5_000L,
            paidPrincipalMinor = 5_501L
        )

        assertThrows(IllegalArgumentException::class.java) {
            PortfolioSummaryMapper.toRecord(installment, "DOP")
        }
    }
}
