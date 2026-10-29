# LedgerPlus

Business Ledger is a native Kotlin/Jetpack Compose local-first Android MVP. See [screen map and rules](docs/PLAN.md).

## Setup

On Windows, clone with `git clone https://github.com/hassanrazmy/LedgerPlus.git`, then open the cloned folder in Android Studio.

Open this directory in Android Studio with JDK 17 and Android SDK 36, then run the `app` configuration. From a configured command line, run `gradle :app:assembleDebug :app:testDebugUnitTest`. No Gradle wrapper binary is included because this workspace had no Gradle installation from which to generate it.

The app stores data on-device in SQLite and requires no account or network permission. First launch asks for business settings. No sample data is inserted. Android system backup is disabled until encrypted backup/restore is implemented.

## Delivered

- Business setup and configurable currency, invoice prefix, stock policy.
- Searchable contacts, products, invoices, purchases, inventory movements, payments and expenses.
- Draft/issue invoice flow, purchase receipt, stock checks, partial invoice/supplier payment allocations, audit/outbox entries.
- Period dashboard, as-of balances, source-linked sales and purchase summaries.
- Pure domain tests for decimal calculations, stock changes, allocations, and sync number conflicts.

## Deferred / release blockers

This is **not production-ready**. Cloud sync and conflict UI, encrypted local storage/backup, authentication/roles, audit identity, full reporting and exports, PDF/printing, barcode scanning, tax rules by jurisdiction, orders/returns, credit management, complete COGS/profit, recurring work, and tablet-specific layouts remain. The app cannot be built in this container because a supported JDK 17, Android SDK and Gradle are unavailable; verify and fix on an Android build host before shipping. See `docs/PLAN.md` for accounting boundaries.
