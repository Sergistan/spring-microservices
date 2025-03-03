create table if not exists products
(
    id       bigserial primary key,
    article_id uuid NOT NULL,
    name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    price DOUBLE PRECISION NOT NULL
);