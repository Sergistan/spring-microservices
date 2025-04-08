create table if not exists accounts
(
    id           serial primary key,
    card_number  varchar(19) NOT NULL UNIQUE,
    amount_money double precision check (amount_money >= 0)
);