create table if not exists accounts
(
    id           bigserial primary key,
    card_number  varchar(256) NOT NULL,
    amount_money double precision check (amount_money >= 0)
);