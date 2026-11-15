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

# 0001: Raw SQL with JdbcClient instead of JPA

## Context

SEVAK is a database project first. Its most important behaviour uses PostgreSQL features directly: window functions (`RANK()`), `LATERAL` joins, `generate_series`, exclusion constraints, advisory locks, materialized views and `EXPLAIN ANALYZE`. Every screen of the web app can also show the SQL behind it, so that SQL has to be the query the app really runs, not one generated at runtime.

## Decision

All database access uses Spring's `JdbcClient` with hand-written SQL, mapped to Java records. There are no JPA entities. One class, `AdminRepository`, also uses `JdbcTemplate`: refreshing the materialized views has to take an advisory lock, refresh and unlock on **one** connection, which needs a `ConnectionCallback`.

## Consequences

- Every query is visible, reviewable and measurable. The benchmark ([BENCHMARKS.md](../BENCHMARKS.md)) reads the same SQL constants the repositories use, so the measured query can't drift from the shipped one, and the "Show the SQL" drawer shows the real query.
- No hidden queries: no lazy loading and no N+1 surprises.
- More code by hand: mapping rows to records, and no automatic relationship loading or dirty checking.
- A schema change can break a query that the compiler can't see. The integration tests run every endpoint against a real PostgreSQL (Testcontainers), so a query that no longer matches the schema fails the build.

## Alternatives considered

- **JPA / Hibernate.** Faster for plain CRUD, but it hides the SQL this project is about, and exclusion constraints, `LATERAL` joins and window functions would end up as native queries anyway.
- **jOOQ.** Type-safe SQL, a good fit for a larger team. Rejected to avoid code generation and another tool; plain SQL reads exactly as it would in `psql`.
- **`JdbcTemplate` everywhere.** `JdbcClient` is the newer, fluent API over the same engine, with named parameters and record mapping built in.
