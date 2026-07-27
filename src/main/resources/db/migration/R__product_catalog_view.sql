-- Flyway R__ repeatable migration: re-applied automatically whenever its checksum changes.
-- Ideal for views, stored procedures and other objects you want to redefine rather than version.
-- The Liquibase counterpart is a changeset marked runOnChange="true"
-- (changes/006-product-catalog-view.xml).
CREATE OR REPLACE VIEW v_product_catalog AS
SELECT p.id            AS product_id,
       p.sku           AS sku,
       p.name          AS product_name,
       p.price         AS price,
       p.stock_quantity AS stock_quantity,
       p.active        AS active,
       c.id            AS category_id,
       c.name          AS category_name
FROM product p
         JOIN category c ON c.id = p.category_id;
