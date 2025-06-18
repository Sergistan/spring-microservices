ALTER TABLE products
    DROP CONSTRAINT IF EXISTS products_article_id_key,
    DROP CONSTRAINT IF EXISTS products_name_key;