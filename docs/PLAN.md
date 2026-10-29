# Business Ledger — screen map, rules, and milestones

## Screen map and flows

- First launch → business setup (name, currency, invoice prefix, negative-stock policy) → dashboard.
- Dashboard → period summary, as-of balances, stock alerts, recent documents → tap a metric to its records.
- Bottom/side navigation → Dashboard, Invoices, Contacts, Products, Purchases, Inventory, Payments, Orders, Expenses, Reports, Settings. Each list has search; relevant lists offer status or type filters.
- Quick create → invoice, purchase, customer, supplier, product, payment, estimate, sales order, purchase order, expense, return, delivery note. Unsupported document types are visibly labelled as planned, not represented as saved records.
- Invoice → choose customer → add catalog lines → edit quantity and price → review totals → save draft or issue → record payment. Purchase follows the same line workflow; receiving it increases stock.
- Inventory → product movement history → manual adjustment with reason. Payments → select open document → allocate amount. Reports → period and basis → traceable source rows.

## Data model

All rows carry stable UUIDs, a business ID, update time, and sync state where appropriate. Amounts and quantities are base-10 strings in SQLite; domain calculations use `BigDecimal`. A configured currency and fractional precision determine display only. The schema is normalized:

- `businesses`: identity, currency, numbering and stock policy.
- `contacts`: customer or supplier master data.
- `products`: catalog, cost, price, threshold, stock tracking.
- `documents` + `document_lines`: invoice or purchase, draft or issued/received, immutable issued values, payments applied.
- `payments` + `allocations`: money movements and document application.
- `stock_movements`: append-only changes with unique source keys for idempotency.
- `expenses`: paid operating costs.
- `outbox` + `audit_events`: local change queue and user-visible history for eventual sync.

## Accounting and inventory rules

- Line net = quantity × unit price − line discount. Tax = line net × tax rate, rounded half-up to the currency scale. Document total = sum of line net and rounded line tax. Discounts cannot exceed gross.
- Invoice issue recognizes the document total as a receivable and, for stock items, records a negative movement. Purchase receipt recognizes a payable and positive stock movement. Drafts never move stock.
- A payment allocation reduces the relevant document balance only. The payment remains a separate cash event. Partial allocations are supported; no allocation may exceed the open balance. Unapplied credits are deferred.
- On-hand = sum of stock movements. Available = on-hand − reserved (reservations are deferred, so MVP available equals on-hand). Negative on-hand is rejected unless explicitly enabled.
- Revenue is issued invoice totals; purchases are received purchase totals. Cash received/paid is separately summed from payments. Profit is deliberately withheld until complete cost-of-goods tracking is available.
- Document numbers are assigned in a single local database transaction at issue/receipt. They are unique per business and kind. Future sync must reserve/resolve numbers before sharing a document and surface collisions; it must never overwrite either UUID. Every write gets an outbox operation ID and audit event.
- Dashboard period figures use document/payment dates in the selected period. Receivables, payables and stock are balances as of today.

## Milestones

1. Project shell and domain rules, runnable Compose entry screen.
2. Local SQLite schema and transaction-safe invoicing, purchases, stock and payments.
3. Dashboard, record screens, reports and settings wired to persisted data.
4. Domain tests and build verification; document gaps before release.

The included implementation is an offline MVP. Cloud sign-in/sync transport, encrypted backup, PDF, tax jurisdiction rules, returns, roles, and advanced stock costing are release work, not represented as complete.
