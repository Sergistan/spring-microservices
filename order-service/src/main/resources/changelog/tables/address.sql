create table if not exists address
(
    id SERIAL PRIMARY KEY,
    city VARCHAR(255) NOT NULL UNIQUE,
    street VARCHAR(255) NOT NULL UNIQUE,
    house_number INTEGER NOT NULL,
    apartment_number INTEGER NOT NULL
);