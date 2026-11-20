create table if not exists couriers
(
    id         bigserial primary key,
    sub_id     uuid        NOT NULL UNIQUE,
    username   VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name  VARCHAR(255),
    email      VARCHAR(255),
    status     VARCHAR(20) NOT NULL,
    created_at timestamp   NOT NULL,
    updated_at timestamp   NOT NULL
);
