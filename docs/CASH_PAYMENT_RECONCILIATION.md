# Cash and payment reconciliation

## Scope

The pure reconciliation engine in `CashPaymentReconciliation` accepts normalized payment and cash-movement snapshots. It reports inconsistencies without changing ledgers or initiating reversals.

## Findings

- `MISSING_CASH_MOVEMENT`: payment has no linked cash movement in the supplied snapshot.
- `AMOUNT_MISMATCH`: linked cash amount does not match the payment amount.
- `CURRENCY_MISMATCH`: linked cash currency differs from payment currency.
- `DUPLICATE_CASH_MOVEMENT`: more than one movement references the same payment.
- `ORPHAN_CASH_MOVEMENT`: linked payment ID is absent from the supplied payment snapshot.
- `INVALID_PAYMENT`: payment has a blank ID/currency or a non-positive amount.

## Integration requirements

1. Supply snapshots from a consistent database transaction/read boundary.
2. Exclude payments confirmed reversed and their reversal cash movements according to the approved reconciliation policy; retain both in audit/history.
3. Match movement types and reversal semantics explicitly before presenting an actionable discrepancy.
4. Reconcile separately per currency; never net different currencies.
5. Display findings as review items. Do not automatically alter balances, delete movements, or reverse operations.
6. Add integration tests for the database snapshot and payment-to-cash link convention before wiring this engine into UI or automated alerts.

## Limitations

This is a pure model/algorithm with unit tests, not yet an integrated database query, dashboard, reconciliation workflow, or server-side control. It requires the integration steps above to be completed and verified.
