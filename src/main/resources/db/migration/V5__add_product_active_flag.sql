-- Flyway V5: schema evolution on an existing table.
-- A column addition plus a backfill in the same migration — the classic expand step of an
-- expand/contract rollout.
ALTER TABLE product
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE product
SET active = FALSE
WHERE stock_quantity = 0;

CREATE INDEX idx_product_active ON product (active);
