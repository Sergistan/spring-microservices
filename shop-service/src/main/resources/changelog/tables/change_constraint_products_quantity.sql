alter table products
    add constraint check_products_quantity_non_negative check (quantity >= 0);
