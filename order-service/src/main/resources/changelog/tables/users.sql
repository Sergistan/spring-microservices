create table if not exists users
(
    id         SERIAL PRIMARY KEY,
    sub_id     VARCHAR(255) NOT NULL UNIQUE,
    username   VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    role       VARCHAR(50)
);