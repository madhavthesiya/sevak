# Architecture Decision Records

Short records of the decisions that shape SEVAK. Each has four parts: the context, the decision, its consequences (good and bad), and the alternatives considered.

| # | Decision |
|---|---|
| [0001](0001-raw-sql-with-jdbcclient-over-jpa.md) | Raw SQL with `JdbcClient` instead of JPA |
| [0002](0002-enforce-business-rules-in-the-database.md) | Enforce business rules in the database, not only in Java |
| [0003](0003-exclusion-constraint-for-double-booking.md) | An exclusion constraint prevents double booking |
| [0004](0004-materialized-views-for-dashboards.md) | Materialized views for dashboards and rankings |
| [0005](0005-stored-procedures-vs-service-layer.md) | What goes in stored procedures and what goes in the service layer |
| [0006](0006-offset-vs-keyset-pagination.md) | OFFSET pagination for most lists, keyset for the large admin list |
| [0007](0007-jwt-authentication.md) | Stateless JWT login with one 24-hour token |
| [0008](0008-row-level-security.md) | Row-level security, so a bug in Java can't show one user another user's data |

All eight are accepted and in force.
