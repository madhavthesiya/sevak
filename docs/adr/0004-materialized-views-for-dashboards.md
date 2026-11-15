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

# 0004: Materialized views for dashboards and rankings

## Context

The admin dashboards (revenue by city and by category) and the provider leaderboard aggregate every booking and payment. On 15 demo bookings they were instant. On 1 million bookings, revenue by category took 4.7 s against the app's 5-second query limit, and the home page's "highest rated" list took 2.3 s ([BENCHMARKS.md](../BENCHMARKS.md)).

## Decision

Three materialized views store these results: `mv_provider_leaderboard`, `mv_city_revenue_summary` and `mv_category_revenue`. Each has a unique index, so it can be refreshed with `REFRESH MATERIALIZED VIEW CONCURRENTLY`, which doesn't block readers.

- A scheduled job refreshes them every 15 minutes (`sevak.views.refresh-interval`). It takes `pg_try_advisory_lock` first, so with two app instances only one refreshes. Lock, refresh and unlock run on one connection, because a session-level lock belongs to a connection.
- Background refreshes have their own 5-minute statement limit. At 1M bookings the normal 5-second limit was cancelling the category refresh, so the summaries would never have updated; measuring found it.
- Admins can refresh on demand (`POST /admin/refresh-views`).
- The live view `v_provider_leaderboard` still exists, and `/admin/leaderboard?fast=true` chooses the stored copy.

## Consequences

- Reads are fast. The revenue chart went from 4,682 ms to 0.019 ms and the home page list from 2,334 ms to 8.0 ms. Search now ranks by the stored leaderboard score: 39.0 ms to 2.4 ms.
- Results can be up to 15 minutes old. A new review changes the ranking at the next refresh.
- Refreshes cost time in the background; the benchmark's "What freshness costs" table measures it.
- `DataCorrectnessTest` checks that the stored category revenue and the stored leaderboard equal their live calculations.

## Alternatives considered

- **Live queries with better indexes.** Tried first. The live leaderboard rewrite went from 3,041 ms to 452 ms, still too slow for a home page.
- **Summary tables kept up to date by triggers.** Always fresh, but every booking and payment write would also update the summaries, adding work and lock contention to the busiest path.
- **An application cache such as Redis.** Another service to run and explain, with no measured need.
