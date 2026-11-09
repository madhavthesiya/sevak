-- ============================================================
--  SEVAK: demo data (repeatable, dev/demo only)
--  Re-running this file replaces ALL data with the demo set.
-- ============================================================

TRUNCATE users, cities, categories, coupons RESTART IDENTITY CASCADE;

INSERT INTO cities (city_name, state) VALUES
('Ahmedabad',  'Gujarat'),
('Mumbai',     'Maharashtra'),
('Bangalore',  'Karnataka'),
('Delhi',      'Delhi'),
('Pune',       'Maharashtra');

-- user_id 1-7 customers, 8-13 providers, 14-15 admins
INSERT INTO users (email, password_hash, role, status, full_name, phone) VALUES
('aarav.sharma@gmail.com',    '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2',  'customer', 'active',   'Aarav Sharma',   '9876543210'),
('priya.patel@gmail.com',     '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2',  'customer', 'active',   'Priya Patel',    '9876543211'),
('rohan.mehta@gmail.com',     '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2',  'customer', 'active',   'Rohan Mehta',    '9876543212'),
('sneha.reddy@gmail.com',     '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2',  'customer', 'active',   'Sneha Reddy',    '9876543213'),
('vikram.singh@gmail.com',    '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2',  'customer', 'active',   'Vikram Singh',   '9876543214'),
('neha.gupta@gmail.com',      '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2',  'customer', 'active',   'Neha Gupta',     '9876543215'),
('arjun.desai@gmail.com',     '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2',  'customer', 'inactive', 'Arjun Desai',    '9876543216'),
('ravi.electrician@gmail.com','$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2',  'provider', 'active',   'Ravi Kumar',     '9876500001'),
('sunita.cleaner@gmail.com',  '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2',  'provider', 'active',   'Sunita Devi',    '9876500002'),
('amit.plumber@gmail.com',    '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2', 'provider', 'active',   'Amit Joshi',     '9876500003'),
('pooja.painter@gmail.com',   '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2', 'provider', 'active',   'Pooja Nair',     '9876500004'),
('karan.carpenter@gmail.com', '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2', 'provider', 'active',   'Karan Malhotra', '9876500005'),
('meena.tutor@gmail.com',     '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2', 'provider', 'inactive', 'Meena Iyer',     '9876500006'),
('admin.raj@sevak.com',       '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2', 'admin',    'active',   'Raj Verma',      NULL),
('admin.divya@sevak.com',     '$2a$10$RwSN2szVDZIaMrJPCx/x9uhtoAG1F.Gk2kf1wiH6HscUjFru5xbz2', 'admin',    'active',   'Divya Rao',      NULL);

INSERT INTO customers (user_id) SELECT generate_series(1, 7);

-- avg_rating and review_count start empty; the review trigger fills them in.
INSERT INTO service_providers (user_id, city_id, bio, experience_years, verification_status, is_active) VALUES
(8,  1, 'Expert electrician with 10+ years of experience.',   10, 'verified', TRUE),
(9,  1, 'Professional home cleaning services.',                5, 'verified', TRUE),
(10, 2, 'Licensed plumber for residential & commercial.',      8, 'verified', TRUE),
(11, 3, 'Creative wall painter and interior decorator.',       6, 'verified', TRUE),
(12, 4, 'Skilled carpenter specializing in custom furniture.', 12, 'verified', TRUE),
(13, 5, 'Home tutor for Mathematics and Science.',             3, 'pending',  FALSE);

INSERT INTO admins (user_id, admin_role, department) VALUES
(14, 'Super Admin',   'Operations'),
(15, 'Support Admin', 'Customer Support');

INSERT INTO admin_permissions (admin_id, permission) VALUES
(1, 'ALL'),
(2, 'READ'),
(2, 'RESOLVE_TICKET');

INSERT INTO areas (city_id, area_name, pincode) VALUES
(1, 'Navrangpura',   '380009'),
(1, 'Satellite',     '380015'),
(2, 'Andheri West',  '400058'),
(2, 'Bandra East',   '400051'),
(3, 'Koramangala',   '560034'),
(3, 'Indiranagar',   '560038'),
(4, 'Connaught Place','110001'),
(4, 'Dwarka',        '110075'),
(5, 'Kothrud',       '411038'),
(5, 'Viman Nagar',   '411014');

INSERT INTO locations (area_id, street, landmark, latitude, longitude) VALUES
(1,  '12, Nehru Road',         'Near CG Road',           23.0300000, 72.5700000),
(2,  '45, SG Highway',         'Opp. Iscon Temple',      23.0225000, 72.5300000),
(3,  '78, Link Road',          'Near Andheri Station',    19.1360000, 72.8370000),
(4,  'A-301, Bandra Complex',  'Near Lilavati Hospital',  19.0600000, 72.8400000),
(5,  '56, 1st Cross Road',     'Koramangala 5th Block',   12.9350000, 77.6200000),
(7,  '10, Janpath Lane',       'Near Palika Bazaar',      28.6330000, 77.2190000),
(8,  'D-22, Sector 10',        'Dwarka Metro Station',    28.5870000, 77.0580000),
(9,  '34, Paud Road',          'Near Kothrud Bus Stop',   18.5070000, 73.8070000),
(10, '89, Airport Road',       'Near Pheonix Mall',       18.5670000, 73.9140000),
(5,  '12, 80 Feet Road',       'Koramangala 4th Block',   12.9280000, 77.6220000);

INSERT INTO customer_addresses (customer_id, location_id, label, house_no) VALUES
(1, 1,  'Home',             'Flat 101'),
(1, 2,  'Office',           '3rd Floor'),
(2, 3,  'Home',             'Flat 12B'),
(3, 4,  'Home',             'A-301'),
(4, 5,  'Home',             'House 56'),
(5, 6,  'Home',             'Flat 7'),
(5, 7,  'Office',           'Block D'),
(6, 8,  'Home',             'House 34'),
(7, 9,  'Home',             'Flat 89'),
(3, 10, 'Office',           '2nd Floor'),
(3, 1,  'Parents Home',     'Flat 202'),
(6, 2,  'Parents Home',     'Flat 11'),
(3, 6,  'Delhi Office',     'Suite 4'),
(2, 5,  'Bangalore Branch', 'Unit 9'),
(7, 1,  'Ahmedabad Home',   'Flat 305');

INSERT INTO categories (category_name, description) VALUES
('Electrical',       'Wiring, appliance repair, and electrical installations'),
('Cleaning',         'Home deep cleaning, kitchen cleaning, bathroom cleaning'),
('Plumbing',         'Pipe fitting, leakage repair, bathroom installations'),
('Painting',         'Wall painting, waterproofing, and texture work'),
('Carpentry',        'Furniture repair, custom builds, and wood polishing'),
('Home Tutoring',    'Academic tutoring for school and college students');

INSERT INTO services (category_id, service_name, base_price, description) VALUES
(1, 'Fan Installation',           200.00, 'Ceiling or wall fan installation'),
(1, 'Full House Wiring',         2500.00, 'Complete house electrical wiring'),
(2, 'Deep Home Cleaning',        1500.00, 'Full house deep cleaning service'),
(2, 'Kitchen Cleaning',           500.00, 'Thorough kitchen deep clean'),
(3, 'Pipe Leakage Repair',        400.00, 'Fix leaking pipes and joints'),
(3, 'Bathroom Fitting',          1200.00, 'Install taps, showers, and fittings'),
(4, 'Single Room Painting',       800.00, 'Paint one room with primer and finish coat'),
(4, 'Full House Painting',       5000.00, 'Complete house interior painting'),
(5, 'Furniture Assembly',         600.00, 'Assemble flat-pack or new furniture'),
(5, 'Door/Window Repair',         350.00, 'Fix hinges, locks, and frames'),
(6, 'Math Tutoring (Grade 10)',   300.00, 'CBSE Grade 10 Mathematics'),
(6, 'Science Tutoring (Grade 12)',400.00, 'CBSE Grade 12 Physics and Chemistry');

INSERT INTO service_variants (service_id, variant_name, price, duration) VALUES
(1,  'Standard',     200.00,  30),
(1,  'Premium',      350.00,  45),
(3,  '1 BHK',       1500.00, 120),
(3,  '2 BHK',       2200.00, 180),
(3,  '3 BHK',       3000.00, 240),
(5,  'Minor Fix',    400.00,  30),
(5,  'Major Repair', 800.00,  90),
(7,  'Standard',     800.00, 240),
(7,  'Premium',     1200.00, 300),
(9,  'Basic',        600.00,  60);

INSERT INTO provider_services (provider_id, service_id, price) VALUES
(1, 1,  220.00),
(1, 2,  2600.00),
(2, 3,  1500.00),
(2, 4,  500.00),
(3, 5,  400.00),
(3, 6,  1250.00),
(4, 7,  800.00),
(4, 8,  5200.00),
(5, 9,  600.00),
(5, 10, 350.00),
(6, 11, 300.00),
(6, 12, 400.00);

INSERT INTO provider_service_areas (provider_id, area_id)
SELECT sp.provider_id, a.area_id
FROM service_providers sp JOIN areas a ON a.city_id = sp.city_id;

INSERT INTO coupons (code, discount_type, discount_value, min_order, usage_limit, valid_from, valid_to) VALUES
('WELCOME10',  'percentage', 10.00, 500.00,  100, '2026-01-01', '2026-12-31'),
('FLAT200',    'flat',       200.00, 1000.00,  50, '2026-03-01', '2026-06-30'),
('SUMMER15',   'percentage', 15.00, 800.00,   30, '2026-04-01', '2026-07-31'),
('MEGA500',    'flat',       500.00, 3000.00,  20, '2026-01-01', '2026-03-31'),
('NEWUSER25',  'percentage', 25.00, 300.00,  200, '2026-01-01', '2026-12-31');

-- scheduled_end = start + the sum of variant durations (60 minutes for an item without a variant).
INSERT INTO bookings (customer_id, provider_id, location_id, coupon_id, scheduled_start, scheduled_end, status, subtotal, discount, total_amount, special_instructions, created_at) VALUES
(1, 1, 1, 1,    '2026-04-05 10:00+05:30', '2026-04-05 11:15+05:30', 'completed',   550.00,   55.00,  495.00, 'Please bring own ladder',            '2026-04-03 18:00+05:30'),
(1, 2, 2, NULL, '2026-04-06 09:00+05:30', '2026-04-06 12:00+05:30', 'completed',  2000.00,    0.00, 2000.00, 'Focus on kitchen and bathrooms',     '2026-04-04 09:00+05:30'),
(2, 3, 3, 2,    '2026-04-07 11:00+05:30', '2026-04-07 12:30+05:30', 'completed',  1600.00,  200.00, 1400.00, 'Leaking pipe in bathroom',           '2026-04-05 20:00+05:30'),
(3, 1, 1, NULL, '2026-04-08 14:00+05:30', '2026-04-08 14:45+05:30', 'confirmed',   350.00,    0.00,  350.00, NULL,                                 '2026-04-06 10:00+05:30'),
(4, 4, 5, 3,    '2026-04-09 10:30+05:30', '2026-04-09 19:30+05:30', 'in_progress', 2000.00, 300.00, 1700.00, 'Light blue color preferred',         '2026-04-07 12:00+05:30'),
(5, 5, 6, NULL, '2026-04-10 16:00+05:30', '2026-04-10 17:00+05:30', 'pending',     600.00,    0.00,  600.00, 'Assemble IKEA wardrobe',             '2026-04-08 16:00+05:30'),
(6, 2, 2, 5,    '2026-04-11 08:00+05:30', '2026-04-11 12:00+05:30', 'completed',  2700.00,  675.00, 2025.00, 'Deep clean before house party',      '2026-04-09 08:00+05:30'),
(3, 5, 6, NULL, '2026-04-12 12:00+05:30', '2026-04-12 13:00+05:30', 'cancelled',   350.00,    0.00,  350.00, 'Repair broken window latch',         '2026-04-10 12:00+05:30'),
(2, 4, 5, NULL, '2026-04-13 15:00+05:30', '2026-04-13 16:00+05:30', 'completed',  5000.00,    0.00, 5000.00, 'Full flat painting - neutral tones', '2026-04-11 15:00+05:30'),
(7, 1, 1, 1,    '2026-04-14 11:00+05:30', '2026-04-14 12:00+05:30', 'pending',    2500.00,  250.00, 2250.00, 'Full house rewiring needed',         '2026-04-12 11:00+05:30');

INSERT INTO booking_items (booking_id, item_no, service_id, variant_id, quantity, unit_price) VALUES
(1,  1, 1,  1,    1, 200.00),    -- Booking 1: Fan Installation – Standard
(2,  1, 3,  3,    1, 1500.00),   -- Booking 2: Deep Cleaning – 1 BHK
(3,  1, 5,  6,    1, 400.00),    -- Booking 3: Pipe Leakage – Minor Fix
(4,  1, 1,  2,    1, 350.00),    -- Booking 4: Fan Installation – Premium
(5,  1, 7,  8,    1, 800.00),    -- Booking 5: Room Painting – Standard
(6,  1, 9,  10,   1, 600.00),    -- Booking 6: Furniture Assembly – Basic
(7,  1, 3,  4,    1, 2200.00),   -- Booking 7: Deep Cleaning – 2 BHK
(7,  2, 4,  NULL, 1, 500.00),    -- Booking 7: + Kitchen Cleaning (no variant)
(8,  1, 10, NULL, 1, 350.00),    -- Booking 8: Door/Window Repair
(9,  1, 8,  NULL, 1, 5000.00),   -- Booking 9: Full House Painting
(10, 1, 2,  NULL, 1, 2500.00),   -- Booking 10: Full House Wiring
(1,  2, 1,  2,    1, 350.00),    -- Booking 1: extra Premium fan
(5,  2, 7,  9,    1, 1200.00),   -- Booking 5: extra Premium room
(3,  2, 6,  NULL, 1, 1200.00),   -- Booking 3: + Bathroom Fitting
(2,  2, 4,  NULL, 1, 500.00);    -- Booking 2: + Kitchen Cleaning

UPDATE coupons c SET used_count = (SELECT COUNT(*) FROM bookings b WHERE b.coupon_id = c.coupon_id);

-- The insert trigger logged each booking's final status; replace that with the full history.
TRUNCATE booking_status_log RESTART IDENTITY;

INSERT INTO booking_status_log (booking_id, old_status, new_status, changed_at, remarks) VALUES
(1,  NULL,          'pending',     '2026-04-03 18:00+05:30', 'Booking placed by customer'),
(1,  'pending',     'confirmed',   '2026-04-03 18:05+05:30', 'Provider accepted'),
(1,  'confirmed',   'in_progress', '2026-04-05 10:00+05:30', 'Provider arrived'),
(1,  'in_progress', 'completed',   '2026-04-05 11:15+05:30', 'Service finished'),
(2,  NULL,          'pending',     '2026-04-04 09:00+05:30', 'Booking placed'),
(2,  'pending',     'confirmed',   '2026-04-04 09:10+05:30', 'Provider accepted'),
(2,  'confirmed',   'in_progress', '2026-04-06 09:00+05:30', 'Provider arrived'),
(2,  'in_progress', 'completed',   '2026-04-06 12:00+05:30', 'Cleaning done'),
(3,  NULL,          'pending',     '2026-04-05 20:00+05:30', 'Booking placed'),
(3,  'pending',     'confirmed',   '2026-04-05 20:15+05:30', 'Provider accepted'),
(3,  'confirmed',   'in_progress', '2026-04-07 11:00+05:30', 'Provider arrived'),
(3,  'in_progress', 'completed',   '2026-04-07 12:30+05:30', 'Leak fixed'),
(4,  NULL,          'pending',     '2026-04-06 10:00+05:30', 'Booking placed'),
(4,  'pending',     'confirmed',   '2026-04-06 10:30+05:30', 'Provider accepted'),
(5,  NULL,          'pending',     '2026-04-07 12:00+05:30', 'Booking placed'),
(5,  'pending',     'confirmed',   '2026-04-07 12:20+05:30', 'Provider accepted'),
(5,  'confirmed',   'in_progress', '2026-04-09 10:30+05:30', 'Painting started'),
(6,  NULL,          'pending',     '2026-04-08 16:00+05:30', 'Booking placed'),
(7,  NULL,          'pending',     '2026-04-09 08:00+05:30', 'Booking placed'),
(7,  'pending',     'confirmed',   '2026-04-09 08:30+05:30', 'Provider accepted'),
(7,  'confirmed',   'in_progress', '2026-04-11 08:00+05:30', 'Provider arrived'),
(7,  'in_progress', 'completed',   '2026-04-11 12:00+05:30', 'Cleaning done'),
(8,  NULL,          'pending',     '2026-04-10 12:00+05:30', 'Booking placed'),
(8,  'pending',     'cancelled',   '2026-04-10 12:30+05:30', 'Customer cancelled - not needed'),
(9,  NULL,          'pending',     '2026-04-11 15:00+05:30', 'Booking placed'),
(9,  'pending',     'confirmed',   '2026-04-11 15:20+05:30', 'Provider accepted'),
(9,  'confirmed',   'in_progress', '2026-04-13 15:00+05:30', 'Painting started'),
(9,  'in_progress', 'completed',   '2026-04-13 16:00+05:30', 'Painting finished'),
(10, NULL,          'pending',     '2026-04-12 11:00+05:30', 'Booking placed');

-- Booking 1 shows a failed attempt followed by a successful retry.
INSERT INTO payments (booking_id, payment_method, status, amount, gateway_ref, paid_at, failure_reason) VALUES
(1,  'Debit Card',  'failed',    495.00, NULL,          NULL,                     'Insufficient balance'),
(1,  'UPI',         'success',   495.00, 'TXN_UPI_001', '2026-04-05 11:20+05:30', NULL),
(2,  'Credit Card', 'success',  2000.00, 'TXN_CC_002',  '2026-04-06 12:05+05:30', NULL),
(3,  'UPI',         'success',  1400.00, 'TXN_UPI_003', '2026-04-07 12:35+05:30', NULL),
(5,  'Debit Card',  'pending',  1700.00, NULL,          NULL,                     NULL),
(7,  'Net Banking', 'success',  2025.00, 'TXN_NB_007',  '2026-04-11 12:30+05:30', NULL),
(9,  'UPI',         'success',  5000.00, 'TXN_UPI_009', '2026-04-13 17:00+05:30', NULL),
(10, 'Credit Card', 'pending',  2250.00, NULL,          NULL,                     NULL),
(8,  'UPI',         'refunded',  350.00, 'TXN_UPI_008', '2026-04-10 12:10+05:30', NULL);

INSERT INTO refunds (payment_id, amount, status, requested_at, processed_at)
SELECT payment_id, amount, 'processed', '2026-04-10 12:30+05:30', '2026-04-11 10:00+05:30'
FROM payments WHERE booking_id = 8;

INSERT INTO cancellations (booking_id, reason, cancelled_by, cancelled_at) VALUES
(8, 'Customer decided the repair was not needed after all.', 3, '2026-04-10 12:30+05:30');

INSERT INTO provider_documents (provider_id, document_type, file_url, description) VALUES
(1, 'Aadhaar Card',  '/docs/ravi_aadhaar.pdf',   'Government ID proof'),
(1, 'Certificate',   '/docs/ravi_electrician_cert.pdf', 'Electrician license'),
(2, 'Aadhaar Card',  '/docs/sunita_aadhaar.pdf',  'Government ID proof'),
(3, 'PAN Card',      '/docs/amit_pan.pdf',         'Tax ID proof'),
(4, 'Aadhaar Card',  '/docs/pooja_aadhaar.pdf',    'Government ID proof'),
(5, 'Aadhaar Card',  '/docs/karan_aadhaar.pdf',    'Government ID proof');

UPDATE provider_documents SET status = 'verified', verified_by = 1, verified_at = '2026-03-20 12:00+05:30';

-- Days are ISO numbers: 1 = Monday ... 7 = Sunday. Ravi has a split shift on Mondays.
INSERT INTO provider_availability (provider_id, day_of_week, start_time, end_time) VALUES
(1, 1, '09:00', '13:00'),
(1, 1, '14:00', '18:00'),
(1, 2, '09:00', '18:00'),
(1, 3, '09:00', '18:00'),
(1, 6, '10:00', '14:00'),
(2, 1, '08:00', '16:00'),
(2, 3, '08:00', '16:00'),
(2, 5, '08:00', '16:00'),
(3, 1, '10:00', '19:00'),
(3, 4, '10:00', '19:00'),
(4, 2, '09:00', '17:00'),
(4, 5, '09:00', '17:00'),
(5, 1, '11:00', '20:00');

INSERT INTO provider_reviews (booking_id, rating, comment) VALUES
(1, 5, 'Excellent work! Fan installed perfectly and quickly.'),
(2, 4, 'Good cleaning but slightly late arrival.'),
(3, 5, 'Fixed the leak in under 30 minutes. Very professional.'),
(7, 4, 'Thorough cleaning, house looks brand new.'),
(9, 3, 'Decent painting job but missed some spots near the ceiling.');

INSERT INTO service_reviews (service_id, booking_id, rating, comment) VALUES
(1, 1, 5, 'Fan Installation service was quick and efficient.'),
(3, 2, 4, 'Deep cleaning was satisfactory.'),
(5, 3, 5, 'Pipe repair was handled expertly.'),
(3, 7, 5, 'Best cleaning service I have ever used!'),
(8, 9, 3, 'Painting quality could be improved.'),
(4, 2, 4, 'Kitchen cleaning was thorough.');

UPDATE provider_reviews r SET created_at = b.scheduled_end + interval '3 hours', updated_at = b.scheduled_end + interval '3 hours'
FROM bookings b WHERE b.booking_id = r.booking_id;

UPDATE service_reviews r SET created_at = b.scheduled_end + interval '3 hours', updated_at = b.scheduled_end + interval '3 hours'
FROM bookings b WHERE b.booking_id = r.booking_id;

INSERT INTO complaints (booking_id, subject, status, description, raised_by, assigned_admin_id, created_at, resolved_at, resolution_notes) VALUES
(5, 'Painter arrived 1 hour late',            'in_progress', 'Arrived at 11:30 instead of 10:30.',             4, 2,    '2026-04-09 12:00+05:30', NULL, NULL),
(8, 'Refund not received after cancellation', 'resolved',    'Refund still not visible in my account.',        3, 2,    '2026-04-10 20:00+05:30', '2026-04-11 10:30+05:30', 'Refund processed by the payment gateway.'),
(9, 'Paint peeling off after 2 days',         'open',        'Paint on the bedroom wall has started peeling.', 2, NULL, '2026-04-15 09:00+05:30', NULL, NULL);

INSERT INTO coupon_redemptions (coupon_id, customer_id, booking_id)
SELECT coupon_id, customer_id, booking_id FROM bookings WHERE coupon_id IS NOT NULL;

-- Keep open bookings in the future so the demo always has upcoming work.
-- Moving by whole weeks keeps each booking on the same weekday.
UPDATE bookings
SET scheduled_start = scheduled_start + w.weeks,
    scheduled_end   = scheduled_end + w.weeks,
    created_at      = now() - interval '1 hour'
FROM (
    SELECT interval '1 week'
           * (floor(extract(epoch FROM now() - timestamptz '2026-04-05 00:00+05:30') / 604800) + 2) AS weeks
) w
WHERE status IN ('pending', 'confirmed');

UPDATE booking_status_log
SET changed_at = now() - CASE new_status WHEN 'pending' THEN interval '1 hour' ELSE interval '30 minutes' END
WHERE booking_id IN (SELECT booking_id FROM bookings WHERE status IN ('pending', 'confirmed'));

UPDATE coupons SET valid_to = GREATEST(valid_to, CURRENT_DATE + 365) WHERE code IN ('WELCOME10', 'NEWUSER25');

REFRESH MATERIALIZED VIEW mv_provider_leaderboard;
REFRESH MATERIALIZED VIEW mv_city_revenue_summary;
