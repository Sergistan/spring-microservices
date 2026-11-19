create table if not exists stock_operations
(
    id         bigserial primary key,
    order_uuid uuid        NOT NULL,
    type       varchar(16) NOT NULL,
    created_at timestamp   NOT NULL,
    CONSTRAINT ux_stock_operations UNIQUE (order_uuid, type)
);
