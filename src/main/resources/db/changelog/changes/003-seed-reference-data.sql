--liquibase formatted sql
--
-- Liquibase changeset 003 — the SQL format.
--
-- When you need raw SQL, Liquibase gets out of the way: this file is valid SQL that Liquibase
-- annotates with comment directives. The trade-off is the same as Flyway's — you lose database
-- portability — but you keep changeset tracking, contexts and an explicit rollback.

--changeset wallaceespindola:003-seed-reference-data context:demo splitStatements:true
--comment: Seed category and product reference data
INSERT INTO category (name, description)
VALUES ('Databases', 'Relational and NoSQL database products');
INSERT INTO category (name, description)
VALUES ('Developer Tools', 'Tooling that supports the software delivery lifecycle');
INSERT INTO category (name, description)
VALUES ('Cloud Infrastructure', 'Managed compute, storage and networking services');

INSERT INTO product (sku, name, description, price, stock_quantity, category_id)
VALUES ('DB-PG-001', 'PostgreSQL Support Plan', 'Enterprise support subscription for PostgreSQL', 4999.00, 25,
        (SELECT id FROM category WHERE name = 'Databases'));
INSERT INTO product (sku, name, description, price, stock_quantity, category_id)
VALUES ('DB-H2-002', 'H2 Embedded Bundle', 'Embedded database bundle for JVM applications', 199.00, 500,
        (SELECT id FROM category WHERE name = 'Databases'));
INSERT INTO product (sku, name, description, price, stock_quantity, category_id)
VALUES ('DT-FW-010', 'Flyway Teams License', 'Versioned SQL migrations with a linear history', 3200.00, 40,
        (SELECT id FROM category WHERE name = 'Developer Tools'));
INSERT INTO product (sku, name, description, price, stock_quantity, category_id)
VALUES ('DT-LB-011', 'Liquibase Pro License', 'Changeset-based migrations with rollback support', 3600.00, 35,
        (SELECT id FROM category WHERE name = 'Developer Tools'));
INSERT INTO product (sku, name, description, price, stock_quantity, category_id)
VALUES ('CI-K8S-100', 'Managed Kubernetes Cluster', 'Production-grade managed Kubernetes control plane', 899.00, 120,
        (SELECT id FROM category WHERE name = 'Cloud Infrastructure'));

--rollback DELETE FROM product;
--rollback DELETE FROM category;
