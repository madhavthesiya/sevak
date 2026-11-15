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

# 0008: Row-level security, so a bug in Java can't show one user another user's data

## Context

Every query that reads private data filters it in SQL (`WHERE customer_id = :id`), and the services check that a booking belongs to the caller before acting on it (ADR 0007). That's one line of defence. A single missing `WHERE`, or a repository method called without the service's check, would show one customer another customer's bookings, payments or addresses, and nothing would fail.

ADR 0002 puts business rules in the database so they hold for every code path. Phase 8 does the same for privacy, using PostgreSQL row-level security (RLS).

## Decision

| Choice | Why | Trade-off |
|---|---|---|
| The API connects as `sevak_app`, which isn't the owner or a superuser and doesn't have `BYPASSRLS` | Owners, superusers and `BYPASSRLS` roles skip every policy without any error | Two logins to manage: the app role and the owner |
| `ENABLE` and `FORCE ROW LEVEL SECURITY` on 16 tables | `FORCE` also filters an ordinary owner. Superusers and `BYPASSRLS` roles still skip it, so the owner role Flyway uses must be one of those | `FORCE` on its own protects nothing if the owner can bypass anyway; the app role is the real protection |
| One trusted setting, `sevak.user_id`, set at the start of every transaction, reads included | The database works out from it whether the caller is a customer, provider or admin, instead of trusting the app for the role too | Anyone who can run arbitrary SQL as `sevak_app` can set it. RLS protects against bugs, not SQL injection; parameterised queries do that |
| With no user set, private rows are invisible | A forgotten setting hides data instead of leaking it | Background jobs need a deliberate way to see everything (below) |
| Policies call their lookups inside `(SELECT ...)` | PostgreSQL then runs each lookup once per query, not once per row | Policies are a little harder to read |
| The lookups are `PARALLEL SAFE` | A function is parallel-unsafe by default, and one unsafe function in a policy turns off parallel query for the whole table | Each must really be safe: they only read a table and a setting, which parallel workers inherit |
| Rows under a booking are visible to whoever can see the booking (`EXISTS` over `bookings`) | One rule, written once; the `bookings` policy decides for its items, payments, refunds, log, cancellations and coupon use | Each child row costs an index lookup on `bookings` |
| Least privilege as well as policies | `sevak_app` has no `TRUNCATE` or DDL, only `SELECT` on the catalog, `UPDATE` on just `status` for bookings, no `INSERT` on the status log or documents, no `UPDATE` on payments, and can't read anyone's password hash, email or phone | Each new table or column needs an explicit grant |
| Policies check whose row it is; column grants decide which values a caller may write | Found in review: a customer could create a refund already processed, a provider could register already verified. Refunds, complaints and provider profiles may only be inserted with the columns the app sets, so the rest take their safe defaults. A new booking must be `pending`, and a customer may only change their booking to `cancelled` | Policies and grants must be read together |
| The owner pool runs only the jobs that need the owner: the view refresh (only an owner may refresh a materialized view) and the demo reset (`TRUNCATE`) | Fixed statements; nothing a user sends reaches that pool | A second, small connection pool |
| The API refuses to start if its requests, or its login, would skip RLS, or if the tables' owner can't | A wrong production login would otherwise silently turn the protection off; an owner without `BYPASSRLS` would be filtered by `FORCE`, breaking the jobs and every definer function | The tests log in as the owner on purpose and must say so (`sevak.db.allow-privileged-login`) |

### Where the database must see every row

A function marked `SECURITY DEFINER` runs as its owner, so it skips the policies. Each one here has a fixed `search_path` (so a caller can't put look-alike tables in front of the real ones), and `PUBLIC` may not execute it. A test fails if a new one breaks either rule.

| What | Why it can't run as the caller |
|---|---|
| `fn_update_provider_avg_rating` (trigger) | A customer's review recalculates the provider's average over every customer's reviews, and customers may not update `service_providers`. As the caller, the update would match no rows and the rating would silently go stale |
| `fn_log_booking_status_change` (trigger) | The app has no `INSERT` on `booking_status_log`, so only this trigger can write the audit trail |
| `fn_mark_payment_refunded` (trigger) | The app can't update `payments`; a payment becomes "refunded" only when an admin processes its refund |
| `fn_check_booking_subtotal` (deferred trigger) | "Booking not found" means "skip the check", so as the caller a booking it couldn't see would pass unchecked |
| `fn_expire_pending_bookings` (scheduled job) | The job has no user and must expire every customer's stale requests. It takes no arguments |
| `fn_provider_busy_times` (public free slots) | Free slots depend on every customer's bookings. It returns only the busy time ranges for one provider |
| `fn_login_credentials` (login) | Login runs before anyone is known, and `sevak_app` can't read password hashes. It returns the one hash for the email being checked |
| `fn_my_contact` (`/me`) | `sevak_app` can't read anyone's email or phone. It returns the caller's own |
| `fn_stored_leaderboard` (admin) | The stored leaderboard holds each provider's email and revenue. The app role may read only the columns search and the home page show; this refuses anyone but an admin, then returns whole rows |
| `fn_live_leaderboard` (admin) | Under the policies the live leaderboard took 2,821 ms instead of 436 ms: the planner, which plans before it knows the caller is an admin, priced a booking check on every payment. It refuses anyone but an admin, then reads the view as its owner (328 ms) |

Checks that only look at the caller's own booking (a review needs a completed booking, a service review needs that service in the booking) still run as the caller, and fail closed when they can't see it.

### Views

- `v_provider_leaderboard` uses `security_invoker = true`. A plain view runs with its owner's rights, so anyone allowed to read it would see totals built from every booking. `sevak_app` can't read it at all: admins reach it through `fn_live_leaderboard`, and its materialized copy is refreshed by the owner, so it is complete.
- `v_public_reviews` deliberately runs with its owner's rights. The reviews on a provider's page are public, but linking a review to its provider and reviewer goes through `bookings`. The view shows only rating, comment, reviewer name and date. It is a plain view, so the page's query still uses the same indexes.
- Materialized views can't have policies. They hold totals per provider, city and category, not anyone's bookings, and only the admin endpoints read their revenue columns.

### Left without RLS, on purpose

`users`, `customers`, `admins`, `admin_permissions`, `locations`, `coupons` and the catalog tables. Provider and reviewer names are public, and login reads `users` before anyone is known, so `users` is protected by columns instead: the app role can read names, role and status, but not the password hash, email or phone. Coupons are shared counters; any caller could change `used_count`, an accepted risk for a demo. `locations` are map points shared between customers, and who lives where is in `customer_addresses`, which has RLS. A test fails if a new table has neither forced RLS nor a place on this list.

## Consequences

- `RowLevelSecurityTest` logs in as `sevak_app` and runs plain SQL, as a buggy query would. Customers and providers see and change only their own rows; with nobody logged in no private row is visible, yet the public screens work; repository methods that trust their caller return nothing for someone else's booking. With RLS turned off, 17 of its 29 tests fail; the other 12 test grants and column privileges, the public screens and the definer functions, which don't depend on RLS.
- Starting the real jar as `postgres` found that the first version of the startup check looked only at the role requests run as. It now refuses a privileged login too.
- A Flyway placeholder inside a `DO $$ ... $$` block is not replaced, so the first version set the app role's password to the literal text `${app_password}`. The login test caught it.
- Every existing test, the web unit tests and the end-to-end journeys pass with requests running as `sevak_app`.
- A review on a real database, before merging, found what the tests had missed:
  - the app role's password was printed in the log on every start, because Flyway prints the rows a callback's `SELECT` returns;
  - with `SEVAK_APP_DB_PASSWORD` unset, production would have given the role the dev password, which is public;
  - the write-side holes above;
  - every user's email and phone, and every provider's revenue, were readable by any request.

  All four are fixed, with a test for each hole. A further test fails if a public service method that reaches the database isn't transactional, because outside a transaction no caller is set.
- Performance, measured on 1M bookings as each screen's real user ([experiment 9](../BENCHMARKS.md#9-row-level-security-what-it-costs-phase-8)): the policies add 0.3 to 0.8 ms to a private screen. Measuring found four problems, all fixed: the policy helpers were parallel-unsafe, which turned off parallel query for every protected table; the admin live leaderboard took 2,821 ms under the policies instead of 436 ms, because the planner priced a booking check on each of about 815,000 payments (it now goes through `fn_live_leaderboard`: 328 ms); the free-slots function was JIT-compiled because of a default row estimate; and the customer spending summary had totalled every payment in the database since before Phase 8 (1,286 ms, now 0.74 ms).

## Alternatives considered

- **Keep only the Java checks.** One missing filter leaks data, and nothing notices.
- **The app sets the role and profile ID as well as the user ID.** Cheaper, but the database would trust three values instead of one.
- **Connect as the owner and rely on `FORCE`.** Doesn't work where the owner is a superuser, as it is in Docker and in most hosted databases.
- **Switch roles per request from one privileged login (`SET ROLE`), as PostgREST does.** The login could switch back with `RESET ROLE`. Here only the tests do this, so their data can roll back, and the startup check refuses it anywhere else.
- **Views with the owner's rights for every screen.** Each one would be a hole in RLS; there is one, for public reviews, and it shows only public columns.
