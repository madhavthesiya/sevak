<div align="center">

# ⬡ SEVAK — Service Marketplace API

**A REST API built on a BCNF-normalized 23-table PostgreSQL schema**

[![CI](https://github.com/madhavthesiya/sevak-api/actions/workflows/ci.yml/badge.svg)](https://github.com/madhavthesiya/sevak-api/actions/workflows/ci.yml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Swagger](https://img.shields.io/badge/Swagger-API%20Docs-85EA2D?logo=swagger&logoColor=black)](https://sevak.madhavv.me/swagger-ui.html)

[Live Demo](https://sevak.madhavv.me/swagger-ui.html) · [API Docs](https://sevak.madhavv.me/api-docs) · [Health Check](https://sevak.madhavv.me/health) · [Architecture](#architecture)

</div>

---

## 📋 Overview

**SEVAK** is a hyperlocal home-services marketplace (similar to Urban Company) where customers can discover, book, and review service providers for tasks like electrical work, plumbing, cleaning, and painting.

This project focuses on **database engineering excellence** — featuring a rigorously normalized schema, advanced SQL analytics, and a clean REST API layer that exposes the full power of PostgreSQL.

### Highlights

- 🏗️ **23-table BCNF-normalized schema** with mathematical proof of compliance
- 📊 **21 REST endpoints** with pagination, showcasing advanced SQL (Window Functions, Derived Tables, STRING_AGG)
- ⚡ **Stored Procedures** for transactional operations (booking cancellation/confirmation)
- 🔄 **Database Triggers** for automatic rating recalculation on review INSERT/UPDATE/DELETE
- 📦 **Materialized Views** for pre-computed analytics (leaderboard, city revenue)
- 📖 **Interactive Swagger UI** for live API exploration
- 🌐 **Live deployment** at [`sevak.madhavv.me`](https://sevak.madhavv.me/swagger-ui.html)

---

## 🏛️ Architecture

```mermaid
graph TD
    A["🌐 Swagger UI — Browser"] -->|HTTP JSON| B["Spring Boot 4.0.4"]

    subgraph API["API Layer"]
        B --> C["Controllers — REST URLs"]
        C --> D["Repositories — Raw SQL"]
        D --> E["JdbcTemplate"]
    end

    subgraph Config["Configuration"]
        F["CORS"]
        G["Error Handler"]
        H["OpenAPI Docs"]
    end

    E -->|JDBC| I["PostgreSQL — Supabase"]

    subgraph DB["sevak schema — 23 Tables"]
        I --> J["users · customers · admins"]
        I --> K["bookings · payments · reviews"]
        I --> L["services · categories · variants"]
        I --> M["locations · areas · cities"]
    end

    subgraph DBFeatures["DB Features"]
        N["Triggers"]
        O["Stored Procedures"]
        P["Indexes"]
    end
```

---

## 🗄️ Database Design

### Entity-Relationship Summary

| Domain | Tables | Description |
|:-------|:------:|:------------|
| **Auth** | 3 | `users`, `customers`, `admins` — ISA hierarchy with role-based access |
| **Providers** | 4 | `service_providers`, `provider_documents`, `provider_availability`, `provider_services` |
| **Services** | 3 | `categories`, `services`, `service_variants` — hierarchical catalog |
| **Location** | 4 | `cities`, `areas`, `locations`, `customer_addresses` — BCNF-compliant geo data |
| **Bookings** | 4 | `bookings`, `booking_items`, `booking_status_log`, `coupons` |
| **Payments** | 2 | `payments`, `cancellations` — 1:1 with bookings |
| **Reviews** | 3 | `provider_reviews`, `service_reviews`, `complaints` |

### BCNF Compliance

The schema underwent a targeted normalization refactor to resolve a hidden dependency:

```
BEFORE (violated BCNF):
  addresses(address_id, customer_id, area_id, street, landmark, label, latitude, longitude)
  FD: {latitude, longitude} → {area_id, street, landmark}  ← NOT a superkey!

AFTER (BCNF compliant):
  locations(location_id, area_id, street, landmark, latitude, longitude)
    → {latitude, longitude} is now a UNIQUE candidate key ✅
  customer_addresses(customer_id, location_id, label)
    → Pure junction table, all-key ✅
```

📄 Full mathematical proof: [`SEVAK_Normalization_Proof.md`](docs/SEVAK_Normalization_Proof.md)

📊 Relational Schema: [`FINAL_SCHEMA.pdf`](docs/FINAL_SCHEMA.pdf)

📝 Top 15 SQL Queries: [`sevak_queries.sql`](sql/sevak_queries.sql)

⚡ Query Optimization Report: [`QUERY_OPTIMIZATION.md`](docs/QUERY_OPTIMIZATION.md)

---

## 🔌 API Endpoints

### 1. General — Browse & Search (Paginated)
| Method | Endpoint | SQL Technique |
|:-------|:---------|:-------------|
| `GET` | `/api/general/services?page=1&limit=10` | JOIN, ORDER BY, LIMIT/OFFSET |
| `GET` | `/api/general/services/search?q={keyword}&page=1&limit=10` | 5-table JOIN, ILIKE, LIMIT/OFFSET |
| `GET` | `/api/general/providers/top?top=5` | JOIN, configurable LIMIT, NULLS LAST |
| `GET` | `/api/general/providers/available?day={day}&page=1&limit=10` | Parameterized JOIN, LIMIT/OFFSET |

### 2. Customer — Bookings & Addresses (Paginated)
| Method | Endpoint | SQL Technique |
|:-------|:---------|:-------------|
| `GET` | `/api/customers/{id}/bookings?page=1&limit=10` | 3-table JOIN, LIMIT/OFFSET |
| `GET` | `/api/customers/{id}/bookings/{bid}/items` | LEFT JOIN, computed columns |
| `GET` | `/api/customers/{id}/payments?page=1&limit=10` | JOIN, NULLS LAST, LIMIT/OFFSET |
| `GET` | `/api/customers/{id}/spending` | CASE, SUM, COUNT, GROUP BY |
| `GET` | `/api/customers/{id}/addresses` | **4-table JOIN** (BCNF path) |

### 3. Provider — Jobs & Analytics (Paginated)
| Method | Endpoint | SQL Technique |
|:-------|:---------|:-------------|
| `GET` | `/api/providers/{id}/pending?page=1&limit=10` | 3-table JOIN via locations, LIMIT/OFFSET |
| `GET` | `/api/providers/{id}/completed?page=1&limit=10` | LEFT JOIN, LIMIT/OFFSET |
| `GET` | `/api/providers/{id}/earnings` | SUM, COUNT, GROUP BY |
| `GET` | `/api/providers/{id}/reviews?page=1&limit=10` | 3-table JOIN via bookings, LIMIT/OFFSET |
| `GET` | `/api/providers/{id}/rating` | **Derived Table (Subquery in FROM)** |

### 4. Admin — Analytics Dashboard
| Method | Endpoint | SQL Technique |
|:-------|:---------|:-------------|
| `GET` | `/api/admin/revenue` | 5-table JOIN, SUM, GROUP BY |
| `GET` | `/api/admin/leaderboard?fast=true` | **RANK() Window Function** / **Materialized View** |
| `GET` | `/api/admin/complaints` | **STRING_AGG**, 5-table JOIN |
| `GET` | `/api/admin/city-revenue` | **Materialized View** (pre-computed 5-table JOIN) |
| `POST` | `/api/admin/refresh-views` | Refreshes both Materialized Views |

### 5. Booking Actions — Stored Procedures
| Method | Endpoint | SQL Technique |
|:-------|:---------|:-------------|
| `POST` | `/api/bookings/{id}/cancel` | **Stored Procedure** (sp_cancel_booking) |
| `POST` | `/api/bookings/{id}/confirm` | **Stored Procedure** (sp_confirm_booking) |

> 🔑 **All `POST` endpoints need an `X-Api-Key` header** (temporary protection until JWT auth is added).
> Locally the key is `dev-admin-key` unless you set `SEVAK_ADMIN_KEY`. In Swagger UI, click **Authorize** and paste the key.

### 6. Health Check
| Method | Endpoint | Description |
|:-------|:---------|:------------|
| `GET` | `/health` | `200` with `"database": "connected"`, or `503` when the database is unreachable |

### Error Responses
Errors use the standard [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) JSON format:

| Status | When |
|:-------|:-----|
| `400` | Invalid parameter (e.g. `page=0`, `limit=500`, `day=Funday`, `id=abc`) |
| `401` | `POST` request without a valid `X-Api-Key` |
| `404` | Unknown URL or booking not found |
| `409` | Booking is in the wrong status for the action (e.g. cancelling a completed booking) |
| `422` | Business rule broken (e.g. provider not available at the booked time) |
| `500` | Unexpected error; details are only written to the server log |

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- PostgreSQL 16+
- Maven 3.9+

### Local Development

```bash
# 1. Clone the repo
git clone https://github.com/madhavthesiya/sevak-api.git
cd sevak-api

# 2. Configure environment
cp .env.example .env
# Edit .env and set your PostgreSQL password

# 3. Set up database
psql -U postgres -c "CREATE DATABASE sevak_db"
# The tables and demo data are created by Flyway when the app first starts.

# 4. Run the API
export DB_PASSWORD=your_password   # Linux/Mac
$env:DB_PASSWORD="your_password"   # Windows PowerShell
mvn spring-boot:run

# 5. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

### Docker (One Command)

```bash
docker compose up --build
# API:     http://localhost:8080/swagger-ui.html
# Health:  http://localhost:8080/health
# POST endpoints: send the header  X-Api-Key: dev-admin-key
# If you ran an older version: docker compose down -v  (removes the old database volume)
```

---

## ✅ Testing

```bash
./mvnw verify   # needs Docker running
```

Tests run against a real PostgreSQL 16 in Docker (Testcontainers), and Flyway builds the database from scratch for every run. Each test is rolled back, so tests can't affect each other. They cover every read endpoint, error responses, stored procedures, triggers, revenue totals, data integrity and customer data isolation. CI runs them on every push, along with a formatting check (Spotless); the coverage report is written to `target/site/jacoco`.

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|:------|:-----------|:--------|
| Runtime | Java 21 (Temurin) | Long-term support JDK |
| Framework | Spring Boot 4.0.4 | REST API + dependency injection |
| Database | PostgreSQL (Supabase) | Managed cloud PostgreSQL |
| DB Access | Spring JDBC (JdbcTemplate) | Raw SQL execution — intentionally chosen over JPA to showcase SQL proficiency |
| Migrations | Flyway | Versioned schema changes; the app builds its own database |
| Testing | JUnit 5, MockMvc, Testcontainers | Integration tests on a real PostgreSQL |
| API Docs | springdoc-openapi 3.0.2 | Auto-generated Swagger UI |
| Container | Docker + Docker Compose | Reproducible deployments |
| API Hosting | DigitalOcean App Platform | Production API deployment |
| DB Hosting | Supabase | Free-tier managed PostgreSQL |

---

## 📁 Project Structure

```
sevak-api/
├── src/main/java/com/sevak/api/
│   ├── SevakApiApplication.java          # Entry point
│   ├── config/
│   │   ├── OpenApiConfig.java            # Swagger metadata
│   │   ├── CorsConfig.java              # Cross-origin config
│   │   └── ApiExceptionHandler.java      # RFC 9457 error responses
│   ├── controller/
│   │   ├── GeneralController.java        # 4 browse/search endpoints
│   │   ├── CustomerController.java       # 5 customer endpoints
│   │   ├── ProviderController.java       # 5 provider endpoints
│   │   ├── AdminController.java          # 5 analytics endpoints
│   │   ├── BookingActionController.java  # 2 stored procedure endpoints
│   │   └── HealthController.java         # /health check
│   └── repository/
│       ├── GeneralRepository.java        # Browse & search queries
│       ├── CustomerRepository.java       # Customer data queries
│       ├── ProviderRepository.java       # Provider analytics queries
│       ├── AdminRepository.java          # Admin dashboard queries
│       └── BookingActionRepository.java  # Stored procedure calls
├── src/main/resources/
│   ├── application.properties            # Dev config
│   ├── application-prod.properties       # Production config
│   └── db/                               # Flyway: migration/ (schema, procedures, views), seed/ (demo data)
├── sql/
│   ├── sevak_queries.sql                 # Top 15 showcase queries
│   └── checks/data_integrity.sql         # Consistency checks (must return 0 rows)
├── Dockerfile                            # Multi-stage build
├── docker-compose.yml                    # Full-stack local setup
└── pom.xml                               # Dependencies
```

---

## 📊 Advanced SQL Showcase

This project intentionally uses **Spring JDBC** instead of JPA/Hibernate to demonstrate advanced SQL capabilities:

<details>
<summary><b>Window Function — Provider Leaderboard</b></summary>

```sql
SELECT
    RANK() OVER (ORDER BY sp.avg_rating DESC NULLS LAST,
                 COUNT(DISTINCT b.booking_id) DESC)  AS rank,
    sp.provider_id, u.email, ci.city_name,
    sp.avg_rating,
    COUNT(DISTINCT b.booking_id)                     AS jobs_completed,
    COALESCE(SUM(p.amount), 0)                       AS total_revenue
FROM service_providers sp
JOIN users u ON sp.user_id = u.user_id
JOIN cities ci ON sp.city_id = ci.city_id
LEFT JOIN bookings b ON sp.provider_id = b.provider_id
                    AND b.status = 'completed'
LEFT JOIN payments p ON b.booking_id = p.booking_id
                    AND p.status = 'success'
WHERE sp.is_active = TRUE
GROUP BY sp.provider_id, u.email, ci.city_name, sp.avg_rating
ORDER BY rank
```
</details>

<details>
<summary><b>Derived Table — Rating vs City Average</b></summary>

```sql
SELECT sp.provider_id, u.email, ci.city_name,
       sp.avg_rating AS my_rating,
       city_avg.avg_city_rating,
       CASE
           WHEN sp.avg_rating > city_avg.avg_city_rating THEN 'Above Average'
           WHEN sp.avg_rating = city_avg.avg_city_rating THEN 'Average'
           ELSE 'Below Average'
       END AS comparison
FROM service_providers sp
JOIN users u ON sp.user_id = u.user_id
JOIN cities ci ON sp.city_id = ci.city_id
JOIN (
    SELECT city_id, ROUND(AVG(avg_rating), 2) AS avg_city_rating
    FROM service_providers WHERE avg_rating IS NOT NULL
    GROUP BY city_id
) city_avg ON sp.city_id = city_avg.city_id
WHERE sp.provider_id = ?
```
</details>

<details>
<summary><b>Stored Procedure — Booking Cancellation</b></summary>

```sql
CALL sp_cancel_booking(booking_id, 'Customer changed mind');
-- Atomically: updates status, creates cancellation record,
-- processes refund if payment exists — all in one transaction
```
</details>

---

## 📡 Sample Response

`GET` [`/api/admin/leaderboard`](https://sevak.madhavv.me/api/admin/leaderboard) — uses `RANK()` Window Function:

```json
[
  {
    "rank": 1,
    "provider_id": 1,
    "email": "ravi.electrician@gmail.com",
    "city_name": "Ahmedabad",
    "avg_rating": 5.00,
    "jobs_completed": 1,
    "total_revenue": 495.00
  },
  {
    "rank": 1,
    "provider_id": 3,
    "email": "amit.plumber@gmail.com",
    "city_name": "Mumbai",
    "avg_rating": 5.00,
    "jobs_completed": 1,
    "total_revenue": 1400.00
  },
  {
    "rank": 3,
    "provider_id": 2,
    "email": "sunita.cleaner@gmail.com",
    "city_name": "Ahmedabad",
    "avg_rating": 4.00,
    "jobs_completed": 2,
    "total_revenue": 4025.00
  }
]
```

> **Note on ranking:** Providers 1 and 3 tie with a 5.00 rating and 1 completed job, so both receive rank 1. Because `RANK()` is used instead of `DENSE_RANK()`, rank 2 is skipped and the next provider receives rank 3.
---

<div align="center">

**© 2026 Madhav Thesiya. All Rights Reserved.**

---

**Made by [Madhav Thesiya](https://www.linkedin.com/in/madhavthesiya/)** — If this was useful, drop a ⭐

</div>
