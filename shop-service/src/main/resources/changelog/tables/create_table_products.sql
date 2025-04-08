create table if not exists products
(
    id       serial primary key,
    article_id uuid NOT NULL unique,
    name VARCHAR(255) NOT NULL unique,
    quantity INTEGER NOT NULL,
    price DOUBLE PRECISION NOT NULL
);