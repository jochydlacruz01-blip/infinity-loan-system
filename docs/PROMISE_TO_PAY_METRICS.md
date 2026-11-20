# Promise-to-pay metrics

The pure summary calculator reports open and overdue promise counts and amounts by DOP, USD and EUR, alongside kept and broken counts.

- Overdue means promised date strictly before the reporting date.
- Open amount totals only OPEN records; closed statuses do not contribute.
- Currency totals are independent; no conversion is applied.
- Duplicate promise IDs and arithmetic overflow are rejected.
- This calculator is a reporting utility, not persistence, payment confirmation, or automated status reconciliation.
