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

# Architecture Decision Records

Short records of the decisions that shape SEVAK. Each has four parts: the context, the decision, its consequences (good and bad), and the alternatives considered.

| # | Decision | In one line |
|--:|---|---|
| [0001](0001-raw-sql-with-jdbcclient-over-jpa.md) | Raw SQL with `JdbcClient` instead of JPA | The database features are the point, and the SQL drawer must show the query that really runs |
| [0002](0002-enforce-business-rules-in-the-database.md) | Enforce business rules in the database | Java checks first for a friendly error; the database enforces the rule on every code path and in races |
| [0003](0003-exclusion-constraint-for-double-booking.md) | An exclusion constraint prevents double booking | The one declarative way to forbid overlapping time ranges; a check in Java races |
| [0004](0004-materialized-views-for-dashboards.md) | Materialized views for dashboards and rankings | 4.7 s down to 0.019 ms, for up to 15 minutes of staleness, refreshed under an advisory lock |
| [0005](0005-stored-procedures-vs-service-layer.md) | Stored procedures or the service layer | Short atomic steps live next to the data; multi-step flows with user-facing errors live in Java |
| [0006](0006-offset-vs-keyset-pagination.md) | OFFSET for most lists, keyset for the big one | Deep OFFSET pages read and discard every earlier row; keyset where it matters, page numbers where they're cheap |
| [0007](0007-jwt-authentication.md) | Stateless JWT login with one 24-hour token | Simple for a separate frontend; refresh tokens are future work |
| [0008](0008-row-level-security.md) | Row-level security | Requests run as `sevak_app` under policies on 16 tables, so a bug in Java can't show one user another user's rows |

> [!TIP]
> Start with [0002](0002-enforce-business-rules-in-the-database.md), which the others build on, then [0008](0008-row-level-security.md), the most recent and the most involved.

All eight are accepted and in force.
