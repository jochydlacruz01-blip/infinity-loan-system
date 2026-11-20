package com.infinityloan.data

import com.infinityloan.finance.PortfolioCurrencySummary
import com.infinityloan.finance.PortfolioInstallmentRecord
import com.infinityloan.finance.PortfolioLoanRecord
import com.infinityloan.finance.PortfolioSummary
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Live local portfolio projection. Currency is inherited from the owning loan;
 * the supplied business date can later be replaced by the server-authoritative date.
 */
class PortfolioSummaryRepository(
    private val loans: LoanDao,
    private val installments: LoanInstallmentDao
) {
    fun observe(asOfDate: LocalDate): Flow<List<PortfolioCurrencySummary>> =
        combine(loans.observeAll(), installments.observeAll()) { loanRows, installmentRows ->
            val loanById = loanRows.associateBy { it.id }
            val installmentRecords = installmentRows.map { row ->
                val loan = requireNotNull(loanById[row.loanId]) { "Una cuota no tiene préstamo asociado" }
                PortfolioSummaryMapper.toRecord(row, loan.currency)
            }
            PortfolioSummary.build(
                loans = loanRows.map(PortfolioSummaryMapper::toRecord),
                installments = installmentRecords,
                asOfDate = asOfDate
            )
        }

    fun observe(asOfDate: Flow<LocalDate>): Flow<List<PortfolioCurrencySummary>> =
        combine(loans.observeAll(), installments.observeAll(), asOfDate) { loanRows, installmentRows, date ->
            val loanById = loanRows.associateBy { it.id }
            val installmentRecords = installmentRows.map { row ->
                val loan = requireNotNull(loanById[row.loanId]) { "Una cuota no tiene préstamo asociado" }
                PortfolioSummaryMapper.toRecord(row, loan.currency)
            }
            PortfolioSummary.build(
                loans = loanRows.map(PortfolioSummaryMapper::toRecord),
                installments = installmentRecords,
                asOfDate = date
            )
        }
}

internal object PortfolioSummaryMapper {
    fun toRecord(loan: LoanEntity) = PortfolioLoanRecord(
        currency = loan.currency,
        status = loan.status,
        outstandingPrincipalMinor = loan.balanceMinor
    )

    fun toRecord(installment: LoanInstallmentEntity, currency: String): PortfolioInstallmentRecord {
        // Late fees and extra charges are separate contract items, not part of totalMinor.
        val paidScheduledMinor = Math.addExact(
            installment.paidInterestMinor,
            installment.paidPrincipalMinor
        )
        val unpaidMinor = Math.subtractExact(installment.totalMinor, paidScheduledMinor)
        require(unpaidMinor >= 0L) { "Los pagos aplicados superan el total programado de la cuota" }
        return PortfolioInstallmentRecord(
            currency = currency,
            dueDate = LocalDate.parse(installment.dueDate),
            unpaidScheduledMinor = unpaidMinor
        )
    }
}
