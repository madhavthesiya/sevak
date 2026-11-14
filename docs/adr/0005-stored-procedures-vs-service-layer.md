# 0005: What goes in stored procedures and what goes in the service layer

## Context

The original project put cancel and confirm in stored procedures (`sp_cancel_booking`, `sp_confirm_booking`). Phase 4 added a much larger operation, creating a booking, together with login, roles and friendly errors. Each new piece of logic needed a home.

## Decision

**Stored procedures and functions** hold short operations that only touch the database and must be atomic and race-safe for any caller:

- `sp_cancel_booking` locks the booking (`SELECT ... FOR UPDATE`), sets the status, records the cancellation and, if it was paid, creates a pending refund.
- `sp_confirm_booking` locks the booking and checks every item and the whole job against the provider's working hours.
- `fn_provider_available`, and `fn_expire_pending_bookings`, which uses `FOR UPDATE SKIP LOCKED` so the expiry job never waits on a booking someone is working on.

**The Java service layer** holds operations that coordinate many steps, need messages for users, or depend on who is logged in. Creating a booking is one `@Transactional` method: claim the idempotency key, check the address and the provider's area, take a per-provider advisory lock, price each item, check the working hours, claim the coupon, insert the booking and its items. Any failure rolls everything back, including the coupon.

Ownership and role checks ("is this your booking?") are always in Java, before any procedure runs.

## Consequences

- Cancel and confirm stay correct under concurrency. `BookingConcurrencyTest` runs them at the same time. Removing only the row lock, or only the status trigger, still passed, because each protects on its own; removing both made the test fail. That defence in depth was found by testing, not assumed.
- Booking creation is easy to read, test and give good errors for, and the database constraints still back it up ([ADR 0002](0002-enforce-business-rules-in-the-database.md)).
- Logic lives in two languages. The rule: data integrity goes in SQL; anything about the request or the user goes in Java.
- Procedures are a repeatable Flyway migration (`R__1_functions.sql`), so they are versioned, reviewed and tested like the rest of the code.

## Alternatives considered

- **Everything in procedures.** Harder to test, poor error messages, and auth would leak into SQL.
- **Everything in Java.** Possible, but cancel and confirm would then depend on every caller remembering the locks. In the database the guarantee holds for any caller.
