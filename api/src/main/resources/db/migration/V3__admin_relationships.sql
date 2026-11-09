-- Task 2.2: admins.permissions was a comma-separated list (a 1NF violation),
-- and no table referenced admins at all.

CREATE TABLE admin_permissions (
    admin_id   INT         NOT NULL REFERENCES admins (admin_id) ON DELETE CASCADE,
    permission VARCHAR(30) NOT NULL
        CHECK (permission IN ('ALL', 'READ', 'RESOLVE_TICKET', 'VERIFY_PROVIDER', 'MANAGE_COUPONS')),
    PRIMARY KEY (admin_id, permission)
);

INSERT INTO admin_permissions (admin_id, permission)
SELECT a.admin_id, trim(p)
FROM admins a, unnest(string_to_array(a.permissions, ',')) AS p;

ALTER TABLE admins DROP COLUMN permissions;

ALTER TABLE complaints
    ADD COLUMN description       TEXT,
    ADD COLUMN raised_by         INT REFERENCES users (user_id),
    ADD COLUMN assigned_admin_id INT REFERENCES admins (admin_id),
    ADD COLUMN created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN resolved_at       TIMESTAMPTZ,
    ADD COLUMN resolution_notes  TEXT;

-- Existing complaints came from the customer who made the booking.
-- raised_by is a real attribute going forward: a provider can complain too.
UPDATE complaints cmp SET raised_by = cu.user_id
FROM bookings b JOIN customers cu ON cu.customer_id = b.customer_id
WHERE b.booking_id = cmp.booking_id;

ALTER TABLE complaints ALTER COLUMN raised_by SET NOT NULL;

ALTER TABLE provider_documents
    ADD COLUMN status      VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'verified', 'rejected')),
    ADD COLUMN verified_by INT REFERENCES admins (admin_id),
    ADD COLUMN verified_at TIMESTAMPTZ,
    ALTER COLUMN uploaded_at TYPE TIMESTAMPTZ USING uploaded_at AT TIME ZONE 'Asia/Kolkata';
