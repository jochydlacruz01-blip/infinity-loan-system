# INFINITY LOAN SYSTEM — release readiness

## Verified repository baseline

- [x] Android application built with Kotlin and Jetpack Compose.
- [x] Room local database schema version 10.
- [x] Sequential migration declarations from schema 1 through 10 are registered (1→2 through 9→10). Full upgrade-path execution from every historical schema still requires dedicated migration tests.
- [x] Loan schedule engine supports French, Italian and American amortization and weekly, biweekly and monthly frequencies.
- [x] Payment allocation uses minor currency units, installment allocations and customer-credit movements.
- [x] Cash sessions, cash movements, close/count/variance flows and integrated loan-payment cash receipt are implemented locally.
- [x] Payment reversal has a dedicated permission, policy, Room transaction, audit record and compensating cash/credit handling.
- [x] CI workflow runs lint, unit tests, debug APK build, release AAB build and Android emulator instrumented tests.
- [x] CI is configured to upload a debug APK and an unsigned release AAB after all preceding steps succeed.
- [x] CI run #335 for commit b51845d completed successfully, including Android instrumented tests and artifact upload.
- [ ] CI run #336 for commit 59156e2 is validating the expanded v1→v10 migration test; do not mark it complete until the run succeeds.

## Release configuration currently observed

- Application ID: `com.infinityloan`
- Version: `0.2.0` (versionCode 2)
- compileSdk / targetSdk: 35
- minSdk: 26
- Java/Kotlin target: 17
- CI release output: unsigned Android App Bundle; not suitable for Play submission until securely signed.
- CI debug output: installable test APK, subject to successful completion and artifact upload.

## Product and production blockers

- [ ] Implement and deploy the shared backend. The API contract in `docs/BACKEND_API_CONTRACT.md` is a specification, not a live service.
- [ ] Server-side identity, session lifecycle, password recovery and authorization; local role checks alone are not sufficient for multi-device production.
- [ ] Shared customer/loan/payment portfolio and idempotent synchronization across Android and web.
- [ ] Server-authoritative business date/time per company/branch and server-side financial posting.
- [ ] Approved and implemented contractual rules for late fees, charges, grace periods, early payments and related reversals.
- [ ] Full general-ledger/accounting integration and independent end-to-end financial reconciliation.
- [ ] Supervisor approval/segregation-of-duties policy for material adjustments and reversals.
- [ ] Customer portal and digital payment-provider integration.
- [ ] Collector assignment/visit workflows and GPS tracking with explicit consent, permissions, retention and privacy controls.
- [ ] Backup/restore, operational monitoring, incident response and disaster recovery.
- [ ] Complete migration tests from every supported historical database version.
- [ ] Security and privacy review, including secure credential/token storage and transport once backend connectivity is introduced.
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

A passing build is not proof that the product is production-ready. Do not submit for public release while any critical financial-integrity, security, backend, privacy or policy gate remains unresolved. Do not describe unsigned CI artifacts as production-signed releases.
