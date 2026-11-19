# INFINITY LOAN SYSTEM — Security hardening and finance integrity phase

Checkpoint: 2026-11-19. This is an implementation plan derived from inspected Kotlin source and tests. It does not claim the listed future controls are implemented.

## Immediate priorities
1. Enforce session and permission checks in domain/use-case services, not solely screen handlers.
2. Make staff administration mutations atomic with checks (including last-active-admin protections under concurrent changes).
3. Verify payment/cash idempotency and authorization at the mutation boundary; retain transactional rollback.
4. Expand tests for permission changes invalidating sessions, disabled-account session rejection, concurrent staff administration, and cash ownership/current-session validation.
5. Add audit coverage matrix for sensitive changes, rejected operations and reversal workflows.
6. Define and persist contract-level late fee/charge policy before wiring the calculator into payment allocation; do not invent policy defaults.
7. For shared deployment, move identity, authorization, financial posting, ledger and idempotency enforcement to a trusted backend.

## Findings from the inspected checkpoint
- Authentication service checks session state and role permissions during staff-account operations, and compares session profile grants/denials with current account state.
- Permission checks use explicit denial precedence.
- Existing tests exercise payment idempotency, cash posting rollback, invalid payment values, and credit payment paths.
- UI/service-local authorization is not a trusted boundary against a modified client.
- Cash and finance operation paths need continued tests for actor identity and authorization at the mutation entry point.
- Contractual late-fee amounts are currently not allocated by payment posting; the calculator alone does not constitute fee collection.

## Exit criteria for production readiness
- Mutation/use-case authorization tests and backend authorization.
- Transactional audit/ledger and controlled reversals.
- Defined fee policy with persisted contract parameters and regression tests.
- Concurrency tests for staff account administration, payment posting, cash sessions and credit balances.
- Backend, backup/restore, sync conflict handling, monitoring, threat model and external security review.
- Signed release build, key management, privacy/regulatory review and operational procedures.
