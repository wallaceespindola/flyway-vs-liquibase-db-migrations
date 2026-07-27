-- Flyway V2: create the product table with a foreign key back to category.
CREATE TABLE product
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku            VARCHAR(50)    NOT NULL,
    name           VARCHAR(200)   NOT NULL,
    description    VARCHAR(1000),
    price          DECIMAL(12, 2) NOT NULL,
    stock_quantity INT            NOT NULL DEFAULT 0,
    category_id    BIGINT         NOT NULL,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT ck_product_price_positive CHECK (price >= 0),
    CONSTRAINT ck_product_stock_non_negative CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_product_sku ON product (sku);
