# SEVAK: Normalization Proof

This document shows that the 29 base tables in the `sevak` schema are in BCNF, under the functional-dependency (FD) assumptions stated below.

**BCNF rule:** for every non-trivial FD `X → Y`, `X` is a superkey. BCNF implies 3NF, 2NF and 1NF.

---

## 1. Assumptions about the real world

Normalization depends on which FDs actually hold, so these are stated rather than implied.

| Possible FD | Holds? | Reasoning |
|---|---|---|
| `{latitude, longitude} → area_id, street, landmark` | Yes | A coordinate identifies one physical point or building. Both columns are `NOT NULL`, so the pair is a true candidate key of `locations`. |
| `{latitude, longitude} → house_no` | **No** | Two flats in one building share coordinates. So `house_no` lives in `customer_addresses`, where it depends on `(customer_id, location_id)`. |
| `pincode → city_id` | Not assumed | Indian PIN codes usually fall in one city but can cross administrative boundaries, so this isn't enforced as an FD. If a project did assume it, `areas` would need a separate `pincodes` table to stay in BCNF. |
| `pincode → area_name` | No | One PIN code covers several localities. |
| `city_name → state` | No | Different states can have cities with the same name. The key of `cities` is `city_id`, and `(city_name, state)` is a candidate key. |
| `service_id → unit_price` | No | `booking_items.unit_price` is the price at booking time and can differ from today's price. |

---

## 2. Table-by-table proof

Unless noted, the only determinants are the listed keys, so every table is in BCNF.

### Identity

| Table | Candidate keys | Notes |
|---|---|---|
| `users` | `user_id`; `email`; `(user_id, role)` is a superkey, declared `UNIQUE` only so subtype tables can reference it | `full_name` and `phone` are facts about the user. |
| `customers` | `customer_id`; `user_id` | `role` is a constant (`CHECK role = 'customer'`). It exists to enforce the ISA constraint and determines nothing else. |
| `service_providers` | `provider_id`; `user_id` | `avg_rating` and `review_count` are controlled redundancy (section 3). |
| `admins` | `admin_id`; `user_id` | `department` doesn't determine `admin_role`. |
| `admin_permissions` | `(admin_id, permission)` | All-key. Replaces a comma-separated column that violated 1NF. |

### Location

| Table | Candidate keys | Notes |
|---|---|---|
| `cities` | `city_id`; `(city_name, state)` | |
| `areas` | `area_id`; `(city_id, area_name)` | See the `pincode` assumption. |
| `locations` | `location_id`; `(latitude, longitude)` | Both candidate keys are NOT NULL. |
| `customer_addresses` | `(customer_id, location_id)` | `label`, `house_no` and `is_active` depend on the whole key. |

**The BCNF fix.** An earlier `addresses(address_id, customer_id, area_id, street, landmark, label, latitude, longitude)` table had the FD `{latitude, longitude} → {area_id, street, landmark}`, and `{latitude, longitude}` was not a superkey because several customers could share one point. Splitting it into `locations` and `customer_addresses` removes the violation.

### Catalog and providers

| Table | Candidate keys | Notes |
|---|---|---|
| `categories` | `category_id`; `category_name` | |
| `services` | `service_id` | Stores `category_id`, not the category name. |
| `service_variants` | `variant_id`; `(service_id, variant_name)`; `(service_id, variant_id)` | `price` depends on the variant, not on the service alone. |
| `provider_services` | `(provider_id, service_id)` | `price` and `is_active` depend on the whole key. A single price per provider would have been a partial dependency in this table's terms. |
| `provider_service_areas` | `(provider_id, area_id)` | All-key. |
| `provider_availability` | `slot_id` | An exclusion constraint forbids overlapping slots for one provider and day. That is a constraint, not an FD. |
| `provider_documents` | `document_id` | |

### Bookings and money

| Table | Candidate keys | Notes |
|---|---|---|
| `coupons` | `coupon_id`; `code` | `used_count` is controlled redundancy (section 3). |
| `coupon_redemptions` | `(coupon_id, customer_id)`; `booking_id` | One use of a coupon per customer. |
| `idempotency_keys` | `(user_id, idempotency_key)` | `request_hash` and `booking_id` depend on the whole key. |
| `bookings` | `booking_id` | Only foreign keys to people and places. `scheduled_end`, `subtotal` and `total_amount` are discussed in section 3. |
| `booking_items` | `(booking_id, item_no)` | `service_id`, `variant_id`, `quantity` and `unit_price` all depend on the whole key. |
| `booking_status_log` | `log_id` | `(booking_id, changed_at)` is deliberately not a key: two changes can happen at the same moment. |
| `booking_status_transitions` | `(from_status, to_status)` | All-key. |
| `payments` | `payment_id` | A booking can have several attempts. A partial unique index allows only one `success` per booking. |
| `refunds` | `refund_id` | A partial unique index allows one non-failed refund per payment. |
| `cancellations` | `cancel_id`; `booking_id` | Refund state moved to `refunds`, removing a contradiction between tables. |

### Feedback

| Table | Candidate keys | Notes |
|---|---|---|
| `provider_reviews` | `review_id`; `booking_id` | The customer and provider are reached through `booking_id`; storing them here would be a transitive dependency. |
| `service_reviews` | `review_id`; `(service_id, booking_id)` | Same reasoning. |
| `complaints` | `complaint_id` | `raised_by` is not derivable from the booking, because either the customer or the provider can raise a complaint. |

---

## 3. Controlled redundancy

A few columns store values that could be calculated. These are **deliberate read-performance choices**, not accidental violations, and each one is kept correct by the database itself.

| Column | Derived from | How it stays correct |
|---|---|---|
| `service_providers.avg_rating`, `review_count` | `provider_reviews` | Trigger on every review insert, update and delete |
| `bookings.subtotal` | `booking_items` | Deferred constraint trigger checks it at commit |
| `bookings.total_amount` | `subtotal - discount` | `CHECK` constraint |
| `coupons.used_count` | bookings using the coupon | Updated atomically on redemption; `CHECK` against `usage_limit` |

`bookings.discount` is a historical fact, not a derived value: the coupon's terms can change later. The same applies to `booking_items.unit_price`.

---

## 4. Constraints that normalization can't express

Some rules span rows or tables, so no normal form covers them. They are enforced separately and each has a test:

- A user's role matches their subtype table (composite foreign keys).
- A booking item's variant belongs to its service (composite foreign key).
- A booking happens at one of that customer's addresses (composite foreign key).
- A provider can't be double-booked (exclusion constraint on a time range).
- Bookings only change status along allowed paths (trigger and `booking_status_transitions`).
- Reviews are only allowed after completion (triggers).

---

## 5. Conclusion

Under the assumptions in section 1, every determinant in every table is a candidate key, so the schema is in **Boyce-Codd Normal Form**. The redundant columns in section 3 are intentional and enforced.
