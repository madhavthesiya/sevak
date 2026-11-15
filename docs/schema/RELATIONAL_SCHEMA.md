<!-- Generated from the database that the Flyway migrations build. Don't edit by hand. -->
<!-- Regenerate: see docs/schema/README.md -->

# SEVAK: relational schema

29 tables in the `sevak` schema. Notation: <ins>primary key</ins> is underlined, *foreign key* is in italics, and a single-column foreign key is followed by `→` and the table it references. Every constraint below is copied from the database itself.

## Identity

**users**(<ins>user_id</ins>, email, password_hash, role, status, created_at, full_name, phone)

- Unique (user_id, role)
- Unique (email)
- Check: `role IN ('customer', 'provider', 'admin')`
- Check: `status IN ('active', 'inactive', 'banned')`

**customers**(<ins>customer_id</ins>, *user_id*, *role*)

- Foreign key (user_id, role) → users(user_id, role)
- On delete: (user_id, role) cascade
- Unique (user_id)
- Check: `role = 'customer'`

**service_providers**(<ins>provider_id</ins>, *user_id*, *city_id* → cities, bio, experience_years, avg_rating, verification_status, is_active, *role*, review_count)

- Foreign key (user_id, role) → users(user_id, role)
- On delete: (user_id, role) cascade
- Unique (user_id)
- Check: `(avg_rating >= 0) AND (avg_rating <= 5)`
- Check: `experience_years >= 0`
- Check: `review_count >= 0`
- Check: `role = 'provider'`
- Check: `verification_status IN ('pending', 'verified', 'rejected')`
- Index `idx_providers_city`: (city_id)
- Row-level security: enabled and forced
- Policy `service_providers_read` (select): using `true`
- Policy `service_providers_register` (insert): check `(user_id = (SELECT app_user_id()))`
- Policy `service_providers_verify` (update): using `(SELECT app_is_admin())`; check `(SELECT app_is_admin())`

**admins**(<ins>admin_id</ins>, *user_id*, admin_role, department, *role*)

- Foreign key (user_id, role) → users(user_id, role)
- On delete: (user_id, role) cascade
- Unique (user_id)
- Check: `role = 'admin'`

**admin_permissions**(<ins>*admin_id*</ins> → admins, <ins>permission</ins>)

- On delete: admin_id cascade
- Check: `permission IN ('ALL', 'READ', 'RESOLVE_TICKET', 'VERIFY_PROVIDER', 'MANAGE_COUPONS')`

## Geography

**cities**(<ins>city_id</ins>, city_name, state)

- Unique (city_name, state)

**areas**(<ins>area_id</ins>, *city_id* → cities, area_name, pincode)

- Unique (city_id, area_name)
- Index `idx_areas_city`: (city_id)

**locations**(<ins>location_id</ins>, *area_id* → areas, street, landmark, latitude, longitude)

- Unique (latitude, longitude)
- Index `idx_locations_area`: (area_id)

**customer_addresses**(<ins>*customer_id*</ins> → customers, <ins>*location_id*</ins> → locations, label, house_no, is_active)

- On delete: customer_id cascade; location_id cascade
- Row-level security: enabled and forced
- Policy `customer_addresses_own` (all): using `(customer_id = (SELECT app_customer_id()))`; check `(customer_id = (SELECT app_customer_id()))`

**provider_service_areas**(<ins>*provider_id*</ins> → service_providers, <ins>*area_id*</ins> → areas)

- On delete: area_id cascade; provider_id cascade
- Index `idx_provider_areas_area`: (area_id)

## Catalog

**categories**(<ins>category_id</ins>, category_name, description)

- Unique (category_name)

**services**(<ins>service_id</ins>, *category_id* → categories, service_name, base_price, description)

- Check: `base_price >= 0`
- Index `idx_services_category`: (category_id)
- Index `idx_services_name_trgm`: gin (service_name gin_trgm_ops)

**service_variants**(<ins>variant_id</ins>, *service_id* → services, variant_name, price, duration)

- On delete: service_id cascade
- Unique (service_id, variant_name)
- Unique (service_id, variant_id)
- Check: `duration > 0`
- Check: `price >= 0`

**provider_services**(<ins>*provider_id*</ins> → service_providers, <ins>*service_id*</ins> → services, price, is_active)

- On delete: provider_id cascade; service_id cascade
- Check: `price >= 0`
- Index `idx_provider_services_service`: (service_id)
- Row-level security: enabled and forced
- Policy `provider_services_add` (insert): check `(provider_id = (SELECT app_provider_id()))`
- Policy `provider_services_change` (update): using `(provider_id = (SELECT app_provider_id()))`; check `(provider_id = (SELECT app_provider_id()))`
- Policy `provider_services_read` (select): using `true`

## Booking and money

**bookings**(<ins>booking_id</ins>, *customer_id* → customers, *provider_id* → service_providers, *location_id* → locations, *coupon_id* → coupons, status, total_amount, special_instructions, scheduled_start, scheduled_end, subtotal, discount, created_at, updated_at)

- Foreign key (customer_id, location_id) → customer_addresses(customer_id, location_id)
- Check: `status IN ('pending', 'confirmed', 'in_progress', 'completed', 'cancelled')`
- Check: `total_amount >= 0`
- Check: `discount >= 0`
- Check: `scheduled_end > scheduled_start`
- Check: `total_amount = (subtotal - discount)`
- Exclusion `ex_provider_no_double_booking`: `EXCLUDE USING gist (provider_id WITH =, tstzrange(scheduled_start, scheduled_end, '[)') WITH &&) WHERE status IN ('pending', 'confirmed', 'in_progress')`
- Index `idx_bookings_customer_start`: (customer_id, scheduled_start DESC)
- Index `idx_bookings_location`: (location_id)
- Index `idx_bookings_pending`: (provider_id, scheduled_start) where `status = 'pending'`
- Index `idx_bookings_provider_status`: (provider_id, status, scheduled_start)
- Index `idx_bookings_start_id`: (scheduled_start DESC, booking_id DESC)
- Trigger `trg_after_booking_status_update`: after insert or update → `fn_log_booking_status_change()`
- Trigger `trg_booking_status_transition`: before update → `fn_check_status_transition()`
- Trigger `trg_booking_subtotal`: after insert or update → `fn_check_booking_subtotal()`
- Trigger `trg_bookings_touch`: before update → `fn_touch_updated_at()`
- Row-level security: enabled and forced
- Policy `bookings_create` (insert): check `((customer_id = (SELECT app_customer_id())) AND ((status)::text = 'pending'::text))`
- Policy `bookings_customer_cancels` (update): using `(customer_id = (SELECT app_customer_id()))`; check `((customer_id = (SELECT app_customer_id())) AND ((status)::text = 'cancelled'::text))`
- Policy `bookings_provider_updates` (update): using `(provider_id = (SELECT app_provider_id()))`; check `(provider_id = (SELECT app_provider_id()))`
- Policy `bookings_read` (select): using `((customer_id = (SELECT app_customer_id())) OR (provider_id = (SELECT app_provider_id())) OR (SELECT app_is_admin()))`

**booking_items**(<ins>*booking_id*</ins> → bookings, <ins>item_no</ins>, *service_id* → services, *variant_id* → service_variants, quantity, unit_price)

- Foreign key (service_id, variant_id) → service_variants(service_id, variant_id)
- On delete: booking_id cascade
- Check: `quantity > 0`
- Check: `unit_price >= 0`
- Index `idx_booking_items_service`: (service_id)
- Trigger `trg_booking_items_subtotal`: after delete or insert or update → `fn_check_booking_subtotal()`
- Row-level security: enabled and forced
- Policy `booking_items_create` (insert): check `(EXISTS (SELECT 1 FROM bookings b WHERE ((b.booking_id = booking_items.booking_id) AND (b.customer_id = (SELECT app_customer_id())))))`
- Policy `booking_items_read` (select): using `(EXISTS (SELECT 1 FROM bookings b WHERE (b.booking_id = booking_items.booking_id)))`

**booking_status_log**(<ins>log_id</ins>, *booking_id* → bookings, old_status, new_status, *changed_by* → users, changed_at, remarks)

- On delete: booking_id cascade
- Index `idx_status_log_booking`: (booking_id, changed_at)
- Row-level security: enabled and forced
- Policy `booking_status_log_read` (select): using `(EXISTS (SELECT 1 FROM bookings b WHERE (b.booking_id = booking_status_log.booking_id)))`

**booking_status_transitions**(<ins>from_status</ins>, <ins>to_status</ins>)


**coupons**(<ins>coupon_id</ins>, code, discount_type, discount_value, min_order, usage_limit, valid_from, valid_to, max_discount, used_count)

- Unique (code)
- Check: `(discount_type <> 'percentage') OR (discount_value <= 100)`
- Check: `(usage_limit IS NULL) OR (used_count <= usage_limit)`
- Check: `valid_to >= valid_from`
- Check: `discount_type IN ('percentage', 'flat')`
- Check: `discount_value > 0`
- Check: `max_discount > 0`
- Check: `used_count >= 0`

**coupon_redemptions**(<ins>*coupon_id*</ins> → coupons, <ins>*customer_id*</ins> → customers, *booking_id* → bookings, redeemed_at)

- On delete: booking_id cascade; coupon_id cascade; customer_id cascade
- Unique (booking_id)
- Row-level security: enabled and forced
- Policy `coupon_redemptions_create` (insert): check `((customer_id = (SELECT app_customer_id())) AND (EXISTS (SELECT 1 FROM bookings b WHERE ((b.booking_id = coupon_redemptions.booking_id) AND (b.customer_id = (SELECT app_customer_id()))))))`
- Policy `coupon_redemptions_read` (select): using `(EXISTS (SELECT 1 FROM bookings b WHERE (b.booking_id = coupon_redemptions.booking_id)))`
- Policy `coupon_redemptions_release` (delete): using `(EXISTS (SELECT 1 FROM bookings b WHERE (b.booking_id = coupon_redemptions.booking_id)))`

**idempotency_keys**(<ins>*user_id*</ins> → users, <ins>idempotency_key</ins>, request_hash, *booking_id* → bookings, created_at)

- On delete: booking_id cascade; user_id cascade
- Row-level security: enabled and forced
- Policy `idempotency_keys_own` (all): using `(user_id = (SELECT app_user_id()))`; check `(user_id = (SELECT app_user_id()))`

**payments**(<ins>payment_id</ins>, *booking_id* → bookings, payment_method, status, amount, gateway_ref, paid_at, created_at, failure_reason)

- Check: `amount >= 0`
- Check: `status IN ('pending', 'success', 'failed', 'refunded')`
- Index `idx_payments_booking`: (booking_id)
- Unique index `uq_one_successful_payment`: (booking_id) where `status = 'success'`
- Row-level security: enabled and forced
- Policy `payments_create` (insert): check `(EXISTS (SELECT 1 FROM bookings b WHERE ((b.booking_id = payments.booking_id) AND (b.customer_id = (SELECT app_customer_id())))))`
- Policy `payments_read` (select): using `(EXISTS (SELECT 1 FROM bookings b WHERE (b.booking_id = payments.booking_id)))`

**refunds**(<ins>refund_id</ins>, *payment_id* → payments, amount, status, requested_at, processed_at)

- Check: `amount > 0`
- Check: `status IN ('pending', 'processed', 'failed')`
- Index `idx_refunds_payment`: (payment_id)
- Unique index `uq_one_active_refund`: (payment_id) where `status <> 'failed'`
- Trigger `trg_refund_processed`: after insert or update → `fn_mark_payment_refunded()`
- Row-level security: enabled and forced
- Policy `refunds_create` (insert): check `(EXISTS (SELECT 1 FROM payments p WHERE (p.payment_id = refunds.payment_id)))`
- Policy `refunds_process` (update): using `(SELECT app_is_admin())`; check `(SELECT app_is_admin())`
- Policy `refunds_read` (select): using `(EXISTS (SELECT 1 FROM payments p WHERE (p.payment_id = refunds.payment_id)))`

**cancellations**(<ins>cancel_id</ins>, *booking_id* → bookings, reason, cancelled_at, *cancelled_by* → users)

- Unique (booking_id)
- Row-level security: enabled and forced
- Policy `cancellations_create` (insert): check `((cancelled_by = (SELECT app_user_id())) AND (EXISTS (SELECT 1 FROM bookings b WHERE (b.booking_id = cancellations.booking_id))))`
- Policy `cancellations_read` (select): using `(EXISTS (SELECT 1 FROM bookings b WHERE (b.booking_id = cancellations.booking_id)))`

## Feedback and provider operations

**provider_reviews**(<ins>review_id</ins>, *booking_id* → bookings, rating, comment, created_at, updated_at)

- Unique (booking_id)
- Check: `(rating >= 1) AND (rating <= 5)`
- Trigger `trg_after_review_change`: after delete or insert or update → `fn_update_provider_avg_rating()`
- Trigger `trg_provider_review_allowed`: before insert → `fn_check_review_allowed()`
- Trigger `trg_provider_reviews_touch`: before update → `fn_touch_updated_at()`
- Row-level security: enabled and forced
- Policy `provider_reviews_read` (select): using `true`
- Policy `provider_reviews_write` (insert): check `(EXISTS (SELECT 1 FROM bookings b WHERE ((b.booking_id = provider_reviews.booking_id) AND (b.customer_id = (SELECT app_customer_id())))))`

**service_reviews**(<ins>review_id</ins>, *service_id* → services, *booking_id* → bookings, rating, comment, created_at, updated_at)

- Unique (service_id, booking_id)
- Check: `(rating >= 1) AND (rating <= 5)`
- Index `idx_svc_reviews_svc`: (service_id)
- Trigger `trg_service_in_booking`: before insert or update → `fn_check_service_in_booking()`
- Trigger `trg_service_review_allowed`: before insert → `fn_check_review_allowed()`
- Trigger `trg_service_reviews_touch`: before update → `fn_touch_updated_at()`
- Row-level security: enabled and forced
- Policy `service_reviews_read` (select): using `true`
- Policy `service_reviews_write` (insert): check `(EXISTS (SELECT 1 FROM bookings b WHERE ((b.booking_id = service_reviews.booking_id) AND (b.customer_id = (SELECT app_customer_id())))))`

**complaints**(<ins>complaint_id</ins>, *booking_id* → bookings, subject, status, description, *raised_by* → users, *assigned_admin_id* → admins, created_at, resolved_at, resolution_notes)

- Check: `status IN ('open', 'in_progress', 'resolved', 'closed')`
- Index `idx_complaints_booking`: (booking_id)
- Index `idx_complaints_status`: (status)
- Row-level security: enabled and forced
- Policy `complaints_create` (insert): check `((raised_by = (SELECT app_user_id())) AND (EXISTS (SELECT 1 FROM bookings b WHERE (b.booking_id = complaints.booking_id))))`
- Policy `complaints_handle` (update): using `(SELECT app_is_admin())`; check `(SELECT app_is_admin())`
- Policy `complaints_read` (select): using `((raised_by = (SELECT app_user_id())) OR (SELECT app_is_admin()))`

**provider_documents**(<ins>document_id</ins>, *provider_id* → service_providers, document_type, file_url, uploaded_at, description, status, *verified_by* → admins, verified_at)

- On delete: provider_id cascade
- Check: `status IN ('pending', 'verified', 'rejected')`
- Index `idx_prov_docs_provider`: (provider_id)
- Row-level security: enabled and forced
- Policy `provider_documents_read` (select): using `((provider_id = (SELECT app_provider_id())) OR (SELECT app_is_admin()))`
- Policy `provider_documents_verify` (update): using `(SELECT app_is_admin())`; check `(SELECT app_is_admin())`

**provider_availability**(<ins>slot_id</ins>, *provider_id* → service_providers, day_of_week, start_time, end_time)

- On delete: provider_id cascade
- Check: `end_time > start_time`
- Check: `(day_of_week >= 1) AND (day_of_week <= 7)`
- Exclusion `ex_availability_no_overlap`: `EXCLUDE USING gist (provider_id WITH =, day_of_week WITH =, timerange(start_time, end_time) WITH &&)`
- Index `idx_availability_day`: (day_of_week)
- Row-level security: enabled and forced
- Policy `provider_availability_add` (insert): check `(provider_id = (SELECT app_provider_id()))`
- Policy `provider_availability_read` (select): using `true`
- Policy `provider_availability_remove` (delete): using `(provider_id = (SELECT app_provider_id()))`

## Views and routines

- Materialized view `mv_category_revenue`, unique index on (category_name)
- Materialized view `mv_city_revenue_summary`, unique index on (city_name)
- Materialized view `mv_provider_leaderboard`, unique index on (provider_id)
- View `v_provider_leaderboard` (runs as the caller)
- View `v_public_reviews` (runs with its owner's rights)
- Function `app_customer_id`
- Function `app_is_admin`
- Function `app_provider_id`
- Function `app_user_id`
- Function `fn_expire_pending_bookings` (security definer)
- Function `fn_live_leaderboard` (security definer)
- Function `fn_login_credentials` (security definer)
- Function `fn_my_contact` (security definer)
- Function `fn_provider_available`
- Function `fn_provider_busy_times` (security definer)
- Function `fn_stored_leaderboard` (security definer)
- Procedure `sp_cancel_booking`
- Procedure `sp_confirm_booking`
