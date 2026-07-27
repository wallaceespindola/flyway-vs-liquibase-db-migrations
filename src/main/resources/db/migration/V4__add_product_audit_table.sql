-- Flyway V4: add an audit trail table.
-- Note what is NOT here: a rollback. Flyway Community has no undo — reverting means writing a new
-- forward migration (V6__drop_product_audit_table.sql). Compare with the Liquibase equivalent,
-- changes/004-add-product-audit-table.xml, which carries an explicit <rollback> block.
CREATE TABLE product_audit
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT       NOT NULL,
    audit_action VARCHAR(20)  NOT NULL,
    changed_by   VARCHAR(100) NOT NULL DEFAULT 'system',
    changed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_audit_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE INDEX idx_product_audit_product ON product_audit (product_id);

INSERT INTO product_audit (product_id, audit_action, changed_by)
SELECT id, 'CREATED', 'flyway-migration'
FROM product;
