create table if not exists deliveries
(
    id           bigserial primary key,
    order_uuid   uuid        NOT NULL UNIQUE,
    courier_id   bigint REFERENCES couriers (id),
    status       VARCHAR(30) NOT NULL,
    paid_at      timestamp   NOT NULL,
    delivered_at timestamp,
    created_at   timestamp   NOT NULL,
    updated_at   timestamp   NOT NULL
);

create index if not exists idx_deliveries_status_paid_at on deliveries (status, paid_at);
