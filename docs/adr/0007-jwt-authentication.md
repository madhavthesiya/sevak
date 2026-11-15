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

# 0007: Stateless JWT login with one 24-hour token

## Context

Phase 4 opened the API to writes: customers book and pay, providers accept jobs, admins verify providers. That needs login, roles and ownership checks. The React frontend is a separate app that calls the API with `fetch`.

## Decision

| Choice | Why | Trade-off |
|---|---|---|
| Stateless JWT, verified by Spring Security's OAuth2 resource server | No session store; simple for a separate frontend | A token can't be revoked before it expires |
| HS256, with the secret in an environment variable | One service both signs and verifies | RS256 would suit several services verifying tokens |
| One 24-hour token, no refresh tokens | Simple, and enough for a demo | Users log in again daily; a banned user keeps access for up to 24 hours |
| BCrypt password hashes | Slow by design, salted | Some CPU per login |
| Token in the `Authorization` header, CSRF protection off | CSRF relies on cookies the browser sends automatically | The frontend must store the token carefully |

Supporting rules:

- `/me/...` routes take the user from the token, never from the URL. Another user's booking returns `404`, not `403`, so IDs can't be probed.
- Login answers the same for an unknown email and a wrong password, and runs BCrypt against a dummy hash for unknown emails, so timing doesn't reveal which accounts exist.
- Registration allows only `customer` and `provider`; nobody can sign up as an admin.
- The production profile has no default secret, so the app won't start without `SEVAK_JWT_SECRET`, and a secret that is too short is refused.
- Login, registration and creating bookings have a strict per-IP rate limit (Bucket4j); everything else has a looser one.

## Consequences

- `SecurityTest` covers login, roles, expired and forged tokens, and other users' bookings and jobs looking like they don't exist. `RateLimitTest` covers the limits.
- The frontend keeps the token in `localStorage`, so a refresh or a new tab stays signed in. Any script on the page could read it, so the app renders no user-supplied HTML and loads no third-party scripts.
- Logging out only deletes the token in the browser.

## Alternatives considered

- **Server sessions in an `HttpOnly` cookie.** Revocable and out of reach of scripts, but needs CSRF protection and a session store, and in development the frontend runs on another origin.
- **Short access tokens plus refresh tokens.** The right choice for a real product, with more moving parts than this demo needs. It's listed as future work.
- **OAuth or social login.** No benefit for a demo that has one-click demo accounts.
