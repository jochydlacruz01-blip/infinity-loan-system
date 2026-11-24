# INFINITY LOAN SYSTEM — release readiness

## Verified repository baseline

- [x] Android application built with Kotlin, Jetpack Compose and Room.
- [x] Room database schema version 15.
- [x] Loan schedule engine supports French, Italian and American amortization and weekly, biweekly and monthly frequencies.
- [x] Financial amounts use integer minor units and checked arithmetic in critical totals and ledgers.
- [x] Payment posting allocates to due installments, updates principal balance and records unapplied customer credit.
- [x] Payment reversal uses a dedicated permission, transactional validation, compensating entries and immutable original payment history.
- [x] Cash sessions, cash movements, cash closing/counting and variance review are implemented locally.
- [x] Collection visits support assignment, status tracking and closure only with a valid, non-reversed payment for the same loan.
- [x] CI runs Android lint, unit tests, debug APK assembly, unsigned release AAB assembly and emulator instrumented tests.
- [x] Workflow run #700 completed successfully, including Android instrumented tests.
- [ ] Workflow run #701 is validating additional assertions for collection-visit/payment ledger integration; update this checklist only after its final result is verified.

## Release configuration currently observed

- Application ID: `com.infinityloan`
- Version: `0.2.0` (versionCode 2)
- compileSdk / targetSdk: 35
- minSdk: 26
- Java/Kotlin target: 17
- CI compiles a debug APK and an unsigned release AAB.
- CI intentionally does not upload APK/AAB files as Actions artifacts; no downloadable artifact is implied by a successful build.
- The release AAB is unsigned and is not suitable for Google Play submission until securely signed.

## Product and production blockers

- [ ] Implement and deploy the shared backend. `docs/BACKEND_API_CONTRACT.md` is a specification, not a live service.
- [ ] Server-side identity, session lifecycle, password recovery, rate limiting and authorization.
- [ ] Shared customer/loan/payment portfolio and idempotent synchronization across Android and future web clients.
- [ ] Server-authoritative business date/time per company/branch and server-side financial posting.
- [ ] Approved and implemented contractual rules for late fees, charges, grace periods, early payments and related reversals.
- [ ] Full general-ledger/accounting integration and independent end-to-end financial reconciliation.
- [ ] Supervisor approval and segregation-of-duties policy for material adjustments and reversals.
- [ ] Customer portal and digital payment-provider integration.
- [ ] GPS tracking with explicit consent, permissions, retention limits and privacy controls.
- [ ] Backup/restore, operational monitoring, incident response and disaster recovery.
- [ ] Complete migration tests from every supported historical database version.
- [ ] Security and privacy review, including protected credential/token storage and secure transport once backend connectivity is introduced.
- [ ] Accessibility, localization, lifecycle, low-memory and supported-device testing.

## Google Play publication gates

- [ ] Confirm the Play-required target API at publication time and update if necessary.
- [ ] Configure Play App Signing and a protected upload key. Never commit keystores, passwords or signing credentials.
- [ ] Produce and verify a signed release AAB using protected CI secrets or a controlled release environment.
- [ ] Confirm final application ID and increment versionCode for each release.
- [ ] Prepare store listing, screenshots, icon, feature graphic, support contact and public privacy policy.
- [ ] Complete Data safety, content rating, target audience, app-access and financial-services declarations accurately.
- [ ] Complete internal/closed testing and resolve pre-launch report findings.
- [ ] Roll out gradually and monitor crashes, ANRs, security reports and user feedback.

## Release rule

A passing build is not proof that the product is production-ready. Do not submit for public release while any critical financial-integrity, security, backend, privacy or policy gate remains unresolved. Do not describe unsigned CI outputs as production-signed releases or claim an APK/AAB download is available unless an artifact has actually been published and verified.
