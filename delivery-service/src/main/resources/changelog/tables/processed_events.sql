create table if not exists processed_events
(
    event_id     uuid PRIMARY KEY,
    processed_at timestamp NOT NULL
);
