create table if not exists orders
(
    id SERIAL PRIMARY KEY,
    order_uuid UUID NOT NULL UNIQUE,
    total_amount DOUBLE PRECISION,
    order_status VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    address INTEGER REFERENCES address(id),
    user_id INTEGER REFERENCES users(id),
    payment_id UUID UNIQUE
);