# Changelog

Notable changes to SEVAK, newest first. Each release links to its pull request, where the full details are. Dates are when the GitHub release was published.

## Unreleased

### Added
- Row-level security ([ADR 0008](docs/adr/0008-row-level-security.md)). The API's requests run as `sevak_app`, a role that doesn't own the tables and can't bypass the policies, with the caller set at the start of every transaction. Policies on 16 tables let customers, providers and admins see and change only their own rows; with no caller, no private row is visible.
- `sevak_app` also gets only the rights it needs: no `TRUNCATE` or DDL, a read-only catalog, `UPDATE` on just a booking's status, no writes to the status log or payments, and no access to password hashes (login reads one hash through `fn_login_credentials`).
- Narrow `SECURITY DEFINER` functions where the database must see every row: the rating, status-log, refund and subtotal triggers, the expiry job, free-slot busy times and login. Each has a fixed `search_path` and can't be run by `PUBLIC`.
- `v_public_reviews`, which shows a provider's reviews without exposing bookings. `v_provider_leaderboard` runs as the caller (`security_invoker`), and admins read it through `fn_live_leaderboard`, which refuses anyone else.
- The API refuses to start if its requests, or its login, would skip row-level security, or if the tables' owner can't bypass it (the jobs and definer functions need that).
- Column privileges: refunds, complaints and provider profiles can only be inserted with the values the app sets; a new booking must be pending; a customer may only cancel; nobody can read another user's email or phone, or a provider's revenue from the stored leaderboard (admins use `fn_stored_leaderboard`, `/me` uses `fn_my_contact`).
- `RowLevelSecurityTest` and `AppRoleCheckTest`; the generated schema documents list every policy.
- Benchmark experiments 9 and 10: each private screen with and without the policies, and the spending summary before and after its rewrite. The benchmark now runs each query as its real user; the Phase 6 results moved to `docs/benchmarks/phase-6/`.

### Changed
- Configuration now has two database logins: `sevak_app` for requests (`SEVAK_APP_DB_PASSWORD`, which Flyway applies on every start) and the owner for Flyway and the maintenance jobs. See `.env.example`.

### Fixed
- Found by measuring row-level security on 1M bookings: the policy helpers turned off parallel query (now `PARALLEL SAFE`); the admin live leaderboard took 2,821 ms under the policies (now 328 ms); the free-slots function was JIT-compiled because of its default row estimate (now `ROWS 20`); and the customer spending summary totalled every payment in the database (1,286 ms, now 0.74 ms).
- The 1M-row load script runs `ANALYZE` before refreshing the stored summaries; otherwise a refresh could be planned on missing statistics and run for minutes.
- A booking with no items is now rejected by the database, not only by the API. The deferred check that compares a booking's subtotal with its items also refuses a booking whose last item is removed.

## [3.0.0] - 2026-11-15: diagrams, docs and launch

### Added
- Schema documents generated from the database that the migrations build: a one-page diagram of all 29 tables (`docs/schema/sevak-schema.svg`), a text relational schema with every key, CHECK, index and trigger, a DBML file, and the README's Mermaid diagram. `SchemaDocsTest` fails the build if any of them drifts from the database.
- The conceptual ER model (`docs/schema/CONCEPTUAL_MODEL.md`): entities, relationships, cardinalities and participation, checked against the schema.
- The conceptual ER diagram in Chen/EER notation (`docs/schema/conceptual-er.svg`, with a PNG and an editable draw.io file).
- The physical diagram is restyled: rounded table cards, colour-coded key badges, nullable types marked with `?`, a built-in legend and lines tinted by domain.
- Seven Architecture Decision Records in `docs/adr/`.
- `docker compose up` also starts the web app, on port 5173.
- This changelog, and a `LICENSE` file: all rights reserved.

### Changed
- The README is rewritten: what it does, architecture, data model, engineering highlights with proof, benchmarks, API, setup, testing and known limits.

### Removed
- The hand-written `docs/sevak_dbml.dbml`, which declared PostgreSQL enum types the schema doesn't use. The old `FINAL_SCHEMA.pdf` moved to `docs/archive/`.

## [2.4.0] - 2026-11-14: benchmarks on 1M bookings

Benchmarks on about 1 million bookings ([docs/BENCHMARKS.md](docs/BENCHMARKS.md)), measured as the median of three `EXPLAIN ANALYZE` runs, with every rewrite checked to return identical rows.

- Home page "highest rated": 2,334 ms → 8 ms. Admin bookings page 5,000: 514 ms → 2.4 ms (new index, migration V13). Live leaderboard: 3,041 ms → 452 ms. Search: 39 ms → 2.4 ms. Admin revenue chart: 4,682 ms → 0.019 ms (new materialized view `mv_category_revenue`).
- Background view refreshes have their own 5-minute limit; the 5-second request limit cancelled them at this size and stopped the app at startup.
- A k6 load test (182 requests/s, p95 548 ms). The connection pool stays at 10 after measuring 20.
- The SQL drawer shows each query's measured time. ([#15](https://github.com/madhavthesiya/sevak/pull/15))

## [2.3.0] - 2026-11-14: React frontend

- A React web app in `web/` for customers, providers and admins, with types generated from the API and a "Show the SQL" drawer on every screen (allow-listed `EXPLAIN ANALYZE` for admins).
- New endpoints: free time slots, price quotes, areas, provider hours and services, monthly earnings, admin queues and the query catalogue.
- Fixed: `GET /me` works for providers and admins, and the API docs publish the Problem error schema.
- **Breaking:** the admin leaderboard returns `providerName` instead of `email`, and `POST /provider/services` returns 204. ([#14](https://github.com/madhavthesiya/sevak/pull/14))

## [2.2.0] - 2026-11-09: login, roles and the booking lifecycle

- JWT login with roles; a transactional create-booking flow with idempotency and coupon limits; the full job lifecycle, payments, reviews, complaints and admin actions.
- Rate limiting, expiry of unanswered requests, and a nightly demo reset.
- **Breaking:** `/customers/{id}/...` becomes `/me/...`, provider jobs and earnings move to `/provider/...`, and most routes need a bearer token. Production needs `SEVAK_JWT_SECRET` and `SEVAK_CORS_ORIGINS`. ([#12](https://github.com/madhavthesiya/sevak/pull/12))

## [2.1.0] - 2026-11-09: typed, versioned API

- A `/api/v1` API organised by feature, with typed responses, pagination metadata, keyset pagination for all bookings, stable error types, Actuator health checks, request IDs and scheduled view refreshes.
- **Breaking:** URLs move to `/api/v1`, JSON fields are camelCase, and `/health` moves to `/actuator/health`. ([#11](https://github.com/madhavthesiya/sevak/pull/11))

## [2.0.0-db] - 2026-11-09: database hardening

- The database enforces the marketplace's rules: no double booking, booking status rules, totals that can't drift, payment attempts and refunds, coupon limits and review rules. 107 tests, including concurrency races.
- **Breaking:** `scheduled_date` and `scheduled_time` are replaced by `scheduled_start` and `scheduled_end`. ([#10](https://github.com/madhavthesiya/sevak/pull/10))

## [1.2.0] - 2026-11-09: engineering foundation

- Flyway migrations, 65 integration tests on a real PostgreSQL (Testcontainers), and GitHub Actions CI. ([#2](https://github.com/madhavthesiya/sevak/pull/2))

## [1.1.0] - 2026-11-07: emergency fixes

- **Fixed:** revenue by category no longer counts a payment once per booking item; consistent seed data; materialized views filled after seeding; a customer can no longer read another customer's booking items; `/health` returns 503 when the database is down; `docker compose up` works on a fresh clone.
- **Added:** RFC 9457 Problem Details errors, input validation for paging parameters, and `sql/checks/data_integrity.sql`.
- **Removed:** duplicate indexes and the outdated `V2_improvements.sql`. ([#1](https://github.com/madhavthesiya/sevak/pull/1))

[3.0.0]: https://github.com/madhavthesiya/sevak/releases/tag/v3.0.0
[2.4.0]: https://github.com/madhavthesiya/sevak/releases/tag/v2.4.0
[2.3.0]: https://github.com/madhavthesiya/sevak/releases/tag/v2.3.0
[2.2.0]: https://github.com/madhavthesiya/sevak/releases/tag/v2.2.0
[2.1.0]: https://github.com/madhavthesiya/sevak/releases/tag/v2.1.0
[2.0.0-db]: https://github.com/madhavthesiya/sevak/releases/tag/v2.0.0-db
[1.2.0]: https://github.com/madhavthesiya/sevak/releases/tag/v1.2.0
[1.1.0]: https://github.com/madhavthesiya/sevak/releases/tag/v1.1.0
