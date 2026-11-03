create table if not exists outbox_events
(
    id         bigserial primary key,
    event_id   uuid      NOT NULL UNIQUE,
    order_uuid uuid      NOT NULL,
    payload    text      NOT NULL,
    created_at timestamp NOT NULL
);
