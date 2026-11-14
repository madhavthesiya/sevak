# 0002: Enforce business rules in the database, not only in Java

## Context

The audit at the start of the rebuild found rules that existed only in Java, or nowhere: revenue was double-counted, the seed data contradicted itself, a provider could be double-booked, and a booking could move from any status to any other. Java checks also race: two requests can both see a slot as free and both insert.

## Decision

Every business rule the schema can express is enforced there. Java checks the same rules first, to give friendly error messages. The database is the guarantee; Java is the polite first check.

- `CHECK` constraints for values: ratings 1 to 5, `total_amount = subtotal - discount`, `end_time > start_time`.
- Composite foreign keys for rules across tables: a user appears only in the subtype table that matches their role, a booking's address belongs to that customer, an item's variant belongs to its service.
- Partial unique indexes: one successful payment per booking, one refund that hasn't failed per payment.
- An exclusion constraint against double booking ([ADR 0003](0003-exclusion-constraint-for-double-booking.md)).
- Triggers for rules that span rows: allowed status changes (stored as rows in `booking_status_transitions`), the status audit log, subtotal equals the sum of the items (a deferred constraint trigger), reviews only after completion, and the cached provider rating.

The full list is generated from the database in [RELATIONAL_SCHEMA.md](../schema/RELATIONAL_SCHEMA.md).

## Consequences

- A bug in Java, a manual SQL fix or a future client can't break these rules. The 1M-booking generator ran with every constraint on, so PostgreSQL rejected any row it got wrong.
- Each rule has a test that tries to break it (`DatabaseConstraintsTest`, `DatabaseTriggersTest`).
- Some rules exist twice: in Java for the message, in SQL for the guarantee. `PgErrors` maps database errors such as an exclusion violation back to the API's error types, so a rule the Java missed still returns a clean `409` or `422`.
- Triggers are less visible than Java code. The generated schema docs list them per table.
- Gaps are found by checking, not assumed away. "A booking has at least one item" was enforced only by the API until Phase 7 found it; it is now part of the deferred subtotal check, with a test that tries to break it.

## Alternatives considered

- **Java only.** Easier to read, but racy and easy to bypass, which is how the original bugs happened.
- **Database only.** Users would see raw constraint errors, and some checks (does this provider cover this area, is the coupon valid for this order) give better messages in Java.
