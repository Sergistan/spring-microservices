create table if not exists payments
(
    id          serial primary key,
    order_uuid  uuid              NOT NULL,
    type        varchar(16)       NOT NULL,
    card_number varchar(19)       NOT NULL,
    amount      double precision  NOT NULL,
    payment_id  uuid              NOT NULL,
    status      varchar(16)       NOT NULL,
    created_at  timestamp         NOT NULL,
    CONSTRAINT uq_payments_order_type UNIQUE (order_uuid, type)
);
