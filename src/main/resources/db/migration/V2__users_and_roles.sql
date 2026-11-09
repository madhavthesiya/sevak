-- Task 2.1: name and phone belong to every user, not only customers,
-- and a user's role must match the subtype table they appear in.

ALTER TABLE users RENAME COLUMN password TO password_hash;
ALTER TABLE users ADD COLUMN full_name VARCHAR(100), ADD COLUMN phone VARCHAR(20);

UPDATE users u SET full_name = c.name, phone = c.phone
FROM customers c WHERE c.user_id = u.user_id;

-- Providers and admins never had a name stored; derive one from the email.
UPDATE users SET full_name = initcap(replace(split_part(email, '@', 1), '.', ' '))
WHERE full_name IS NULL;

ALTER TABLE users ALTER COLUMN full_name SET NOT NULL;
ALTER TABLE customers DROP COLUMN name, DROP COLUMN phone;

ALTER TABLE users ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'Asia/Kolkata';

-- ISA constraint: each subtype row carries a fixed role and references (user_id, role),
-- so a customer user can't have a provider row, and a role can't change while a subtype row exists.
ALTER TABLE users ADD CONSTRAINT uq_users_id_role UNIQUE (user_id, role);

ALTER TABLE customers
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'customer' CHECK (role = 'customer'),
    DROP CONSTRAINT customers_user_id_fkey,
    ADD CONSTRAINT fk_customers_user_role FOREIGN KEY (user_id, role)
        REFERENCES users (user_id, role) ON DELETE CASCADE;

ALTER TABLE service_providers
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'provider' CHECK (role = 'provider'),
    DROP CONSTRAINT service_providers_user_id_fkey,
    ADD CONSTRAINT fk_providers_user_role FOREIGN KEY (user_id, role)
        REFERENCES users (user_id, role) ON DELETE CASCADE;

ALTER TABLE admins
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'admin' CHECK (role = 'admin'),
    DROP CONSTRAINT admins_user_id_fkey,
    ADD CONSTRAINT fk_admins_user_role FOREIGN KEY (user_id, role)
        REFERENCES users (user_id, role) ON DELETE CASCADE;
