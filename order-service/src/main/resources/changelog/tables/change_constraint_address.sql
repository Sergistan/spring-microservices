ALTER TABLE address
    DROP CONSTRAINT IF EXISTS address_city_key,
    DROP CONSTRAINT IF EXISTS address_street_key;