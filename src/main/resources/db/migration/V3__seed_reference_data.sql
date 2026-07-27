-- Flyway V3: seed reference data.
-- Seeding through a versioned migration means the data is part of the schema history and is applied
-- exactly once, in order, on every environment.
INSERT INTO category (name, description)
VALUES ('Databases', 'Relational and NoSQL database products'),
       ('Developer Tools', 'Tooling that supports the software delivery lifecycle'),
       ('Cloud Infrastructure', 'Managed compute, storage and networking services');

INSERT INTO product (sku, name, description, price, stock_quantity, category_id)
VALUES ('DB-PG-001', 'PostgreSQL Support Plan', 'Enterprise support subscription for PostgreSQL', 4999.00, 25,
        (SELECT id FROM category WHERE name = 'Databases')),
       ('DB-H2-002', 'H2 Embedded Bundle', 'Embedded database bundle for JVM applications', 199.00, 500,
        (SELECT id FROM category WHERE name = 'Databases')),
       ('DT-FW-010', 'Flyway Teams License', 'Versioned SQL migrations with a linear history', 3200.00, 40,
        (SELECT id FROM category WHERE name = 'Developer Tools')),
       ('DT-LB-011', 'Liquibase Pro License', 'Changeset-based migrations with rollback support', 3600.00, 35,
        (SELECT id FROM category WHERE name = 'Developer Tools')),
       ('CI-K8S-100', 'Managed Kubernetes Cluster', 'Production-grade managed Kubernetes control plane', 899.00, 120,
        (SELECT id FROM category WHERE name = 'Cloud Infrastructure'));
