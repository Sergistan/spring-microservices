create table if not exists product_infos
(
    id SERIAL PRIMARY KEY,
    article_id UUID NOT NULL UNIQUE,
    quantity INTEGER NOT NULL,
    order_id INTEGER REFERENCES orders(id)
);