-- Task 2.8: several non-overlapping slots per day. Days are ISO numbers (1 = Monday),
-- which removes all 'Monday' vs 'monday' string problems.

CREATE TYPE timerange AS RANGE (subtype = time);

ALTER TABLE provider_availability RENAME TO provider_availability_old;

CREATE TABLE provider_availability (
    slot_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id INT      NOT NULL REFERENCES service_providers (provider_id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time  TIME     NOT NULL,
    end_time    TIME     NOT NULL CHECK (end_time > start_time),
    CONSTRAINT ex_availability_no_overlap
        EXCLUDE USING gist (provider_id WITH =, day_of_week WITH =, timerange(start_time, end_time) WITH &&)
);

INSERT INTO provider_availability (provider_id, day_of_week, start_time, end_time)
SELECT provider_id,
       array_position(ARRAY['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'], day_of_week::text),
       start_time,
       end_time
FROM provider_availability_old;

DROP TABLE provider_availability_old;
