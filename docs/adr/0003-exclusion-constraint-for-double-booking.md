<p align="center">
  <a href="../../README.md"><picture>
    <source media="(prefers-color-scheme: dark)" srcset="../images/logo-dark.png">
    <img alt="SEVAK" src="../images/logo-light.png" height="30">
  </picture></a>
</p>

<p align="center">
  <b>Decisions</b> &nbsp;·&nbsp;
  <a href="../BENCHMARKS.md">Benchmarks</a> &nbsp;·&nbsp;
  <a href="../schema/README.md">Schema</a> &nbsp;·&nbsp;
  <a href="../schema/CONCEPTUAL_MODEL.md">Conceptual model</a> &nbsp;·&nbsp;
  <a href="../SEVAK_Normalization_Proof.md">Normalization</a> &nbsp;·&nbsp;
  <a href="../DEPLOYMENT.md">Deployment</a>
</p>

# 0003: An exclusion constraint prevents double booking

## Context

A provider must never have two active bookings at overlapping times. It is the most important rule in a booking product. Checking in Java ("is this slot free?", then insert) races: two requests can both see the slot free and both insert. A `UNIQUE` constraint can't express "these time ranges overlap".

## Decision

A GiST exclusion constraint on `bookings`:

```sql
EXCLUDE USING gist (
    provider_id WITH =,
    tstzrange(scheduled_start, scheduled_end, '[)') WITH &&
) WHERE (status IN ('pending', 'confirmed', 'in_progress'))
```

- `[)` lets a booking start exactly when the previous one ends.
- The `btree_gist` extension lets the GiST index handle the plain equality on `provider_id`.
- The `WHERE` clause means a cancelled or completed booking frees its slot.
- A `pending` booking already holds the slot, so two customers can't request the same time while the provider decides. Ignored requests expire after 24 hours, or at the start time if sooner, so they can't block a slot for ever.
- The create-booking transaction also takes a per-provider advisory lock and checks the slot in Java first, for a clear message. If two requests still race, the constraint rejects the second and the API returns `409 slot-taken`.

## Consequences

- Double booking is impossible however the row is written. `BookingConcurrencyTest.twoCustomersBookingTheSameSlotOnlyOneWins` and `ApiConcurrencyTest.twoCustomersForTheSameSlotOneGetsIt` send real concurrent requests and check that exactly one succeeds.
- In the k6 load test on 1M bookings, 216 attempts were correctly refused with `409 slot-taken`.
- The 1M-row generator had to calculate free slots, because the constraint rejected overlapping rows.
- The GiST index costs some write time. Benchmark experiment 7 found it isn't what makes the free-slots query fast; its job is correctness.

## Alternatives considered

- **Check, then insert, in Java.** Races without extra locking.
- **Locking the provider row, or `SERIALIZABLE` isolation.** Both work for this app's own writes but protect nothing written outside it, and `SERIALIZABLE` needs retry logic around every write.
- **Fixed slots with `UNIQUE (provider_id, slot)`.** Simple, but jobs have different lengths, so one job would need several slot rows.
