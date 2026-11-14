# 0006: OFFSET pagination for most lists, keyset for the large admin list

## Context

Lists return pages. `LIMIT ... OFFSET n` is simple and allows "jump to page 37", but the database still reads and throws away `n` rows, so deep pages get slower. The admin's list of all bookings is the one list that grows without limit: 1 million rows in the benchmark.

## Decision

- **OFFSET** for lists that stay small per user, per provider or per search: the service catalogue, provider search, a provider's reviews and jobs, and a customer's bookings and payments. They return `{ data, page, limit, total, totalPages }`, so the UI can show page numbers.
- **Keyset** for `GET /admin/bookings`. The client sends the last row's sort key, and the query continues after it:

```sql
WHERE (b.scheduled_start, b.booking_id) < (:before, :beforeId)
ORDER BY b.scheduled_start DESC, b.booking_id DESC
```

`booking_id` breaks ties, so no row is skipped or repeated when two bookings start at the same moment. Migration V13 adds the index the keyset needs, `(scheduled_start DESC, booking_id DESC)`.

## Consequences

Measured on 1M bookings ([benchmark experiment 5](../BENCHMARKS.md)), page 5,000:

| | No index | With the V13 index |
|---|---:|---:|
| OFFSET | 881 ms | 492 ms |
| Keyset | 514 ms | **2.4 ms** |

- A keyset page stays fast at any depth. With the index, OFFSET is fast for page 1 (3.2 ms) but still reads and discards 99,980 rows to reach page 5,000.
- Keyset alone wasn't enough. Until V13 nothing indexed its sort key, so every page, including page 1, sorted the whole table. The benchmark found it.
- Keyset can't jump to an arbitrary page and returns no total: a client can only ask for the next page. The endpoint is API-only for now; the web app has no admin bookings screen.

## Alternatives considered

- **OFFSET everywhere.** Simple, but slow for deep pages of the biggest table.
- **Keyset everywhere.** Loses page numbers on lists short enough that OFFSET costs nothing measurable.
