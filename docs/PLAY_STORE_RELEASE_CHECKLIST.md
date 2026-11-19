# INFINITY LOAN SYSTEM — Google Play release checklist

This checklist separates verified engineering work from release blockers. A successful debug APK build is not a production release approval.

## Current verified baseline

- [x] Native Android app using Kotlin and Jetpack Compose.
- [x] Room database with migrations through schema version 5.
- [x] Loan amortization methods: French, Italian and American.
- [x] Weekly, biweekly and monthly installment schedules.
- [x] Payment allocation ledger and customer-credit movements.
- [x] Unit and emulator-based instrumentation tests in GitHub Actions.
- [x] Debug APK artifact in CI.
- [ ] Release AAB build verified in CI (workflow added; run #157 pending at time of this checklist).
- [ ] Signed release AAB.

## Product completion blockers

- [ ] Shared authenticated backend for web and Android, with documented API contracts.
- [ ] Secure sign-in, session lifecycle, password reset and account recovery.
- [ ] Server-enforced role-based access control for administrator, supervisor, cashier and collector.
- [ ] Customer records and complete customer account history.
- [ ] Cash register, cash sessions, receipts and reconciliation.
- [ ] Collector operations, assignments, visit records and configurable location permissions.
- [ ] Accounting ledger and financial reports with currency-aware reconciliation.
- [ ] Customer portal and digital payment provider integration.
- [ ] Reliable synchronization, retry handling, conflict resolution and offline behavior.
- [ ] Audit trail for sensitive financial and administrative actions.
- [ ] Explicitly approved business rules for early payments, late fees, charges, refunds and reversals.

## Security and reliability gates

- [ ] Threat model and security review.
- [ ] No secrets, signing keys, API credentials or private endpoints committed to the repository.
- [ ] Production backend authorization tests; never rely only on hidden UI controls.
- [ ] Encrypted transport and secure storage for credentials/tokens.
- [ ] Backup, restore, retention and disaster-recovery procedures.
- [ ] Database migration tests across every supported historical schema.
- [ ] Financial invariants and ledger reconciliation tests.
- [ ] Accessibility, localization, low-memory, lifecycle and supported-device testing.
- [ ] Crash reporting and privacy-reviewed diagnostics.
- [ ] No unresolved critical or high-severity defects.

## Google Play publication gates

- [ ] Confirm current Play target API requirement before release; update target SDK as required.
- [ ] Configure a protected upload key and Play App Signing; keep key material outside Git.
- [ ] Configure release signing through protected CI secrets or a controlled release environment.
- [ ] Set final application ID, version code and version name.
- [ ] Generate and verify a signed Android App Bundle (AAB).
- [ ] Prepare store listing, screenshots, icon, feature graphic and support contact.
- [ ] Publish a public privacy policy and complete Data safety declarations accurately.
- [ ] Complete content rating, target audience and app-access declarations.
- [ ] Declare financial features and any applicable financial-services information accurately.
- [ ] Complete internal/closed testing and resolve pre-launch report findings.
- [ ] Roll out gradually and monitor crashes, ANRs and user feedback.

## Release rule

Do not label the app production-ready or submit it for public release while any critical product, security, financial-integrity or Play policy gate remains unresolved. The release AAB must be signed using protected credentials; an unsigned CI artifact is only a build-validation output.
