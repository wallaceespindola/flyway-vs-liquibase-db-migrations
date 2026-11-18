# Glossary

Every acronym used in this project's documentation (README, `CLAUDE.md`, `docs/`, articles and slides),
with what it stands for and, where it matters, how this project uses it.

| Acronym | Stands for | In this project |
| --- | --- | --- |
| AI | Artificial Intelligence | Mentioned only in article-platform rules (disclosure settings). |
| ALv2 | Apache License, version 2.0 | The license Liquibase Community's FSL converts to after two years (`FSL-1.1-ALv2`). This repo itself is Apache 2.0. |
| API | Application Programming Interface | The REST endpoints under `/api/v1`, and each engine's embedded Java API (Flyway's `info()`). |
| BOM | Bill of Materials | The Spring Boot dependency BOM that picks the Flyway, Liquibase and H2 versions; they are deliberately not pinned. |
| C4 | Context, Containers, Components, Code | The architecture-diagram model the component diagram follows. |
| CI | Continuous Integration | The GitHub Actions workflows: `ci.yml` (build, tests, coverage gate, smoke test) and `docs.yml` (Markdown lint, link check). |
| CLA | Contributor License Agreement | Required from Liquibase contributors since 5.0. |
| CLI | Command-Line Interface | Flyway and Liquibase command-line tools, and tools such as `marp-cli` and `mermaid-cli`. |
| CMS | Content Management System | A publishing platform's editor (banner sizes per platform). |
| CRC32 | Cyclic Redundancy Check, 32-bit | The checksum Flyway stores per migration in `flyway_schema_history`. |
| CSS | Cascading Style Sheets | Styling of the static dashboard in `src/main/resources/static`. |
| DDL | Data Definition Language | SQL that changes structure (`CREATE TABLE`, `ALTER TABLE`, `CREATE VIEW`). |
| DML | Data Manipulation Language | SQL that changes rows (`INSERT`, `UPDATE`), such as the seed data and the `005b` backfill. |
| DTO | Data Transfer Object | The Java records returned by the REST API (`MigrationStatusReport`, `ComparisonReport`, and others). |
| ERD | Entity-Relationship Diagram | `docs/diagrams/06-erd.*`: the business tables and the two bookkeeping tables. |
| FAQ | Frequently Asked Questions | Liquibase's licensing FAQ, cited for the FSL wording. |
| FK | Foreign Key | `fk_product_category`, `fk_product_audit_product`. |
| FSL | Functional Source License | Liquibase Community's license since 5.0 (`FSL-1.1-ALv2`): source-available, not OSI open source, converts to Apache 2.0 after two years. |
| HTML | HyperText Markup Language | The dashboard page and Marp's rendering format for the slides. |
| HTTP | HyperText Transfer Protocol | The REST API transport on port 8080. |
| IDE | Integrated Development Environment | IntelliJ IDEA or VS Code, both of which can preview the PlantUML diagrams. |
| ISO | International Organization for Standardization | ISO-8601 date-time strings, the Jackson 3 default for `timestamp` fields. |
| JDBC | Java Database Connectivity | The plain-JDBC data access (`JdbcTemplate`) used instead of JPA. |
| JDK | Java Development Kit | Java 25 is required to build and run. |
| JPA | Jakarta Persistence API | Deliberately not used; both engines are compared at the JDBC level. |
| JS | JavaScript | `static/js/app.js`, the dashboard script; `esc()` and `num()` are its XSS choke points. |
| JSON | JavaScript Object Notation | Response format of every REST endpoint, wrapped in `ApiResponse<T>`. |
| JVM | Java Virtual Machine | The single process that hosts both engines. |
| MD5 | Message Digest 5 | The checksum family Liquibase stores per changeset in `DATABASECHANGELOG`. |
| OSI | Open Source Initiative | The body whose definition of open source the FSL does not meet. |
| PDF | Portable Document Format | The Marp export of the deck, the format Speaker Deck accepts. |
| PK | Primary Key | The `id` columns of `category`, `product` and `product_audit`. |
| PNG | Portable Network Graphics | Rendered diagrams and banners. |
| PPTX | PowerPoint Open XML presentation | The editable deck built by `docs/presentations/build_pptx.py`. |
| PRD | Product Requirements Document | `docs/specs/PRD_Specs.md`, the original requirements. |
| REST | Representational State Transfer | Style of the `/api/v1` HTTP endpoints. |
| SQL | Structured Query Language | Flyway's only migration format here, and one of Liquibase's four. |
| SVG | Scalable Vector Graphics | Banner sources next to the PNGs. |
| TL;DR | Too Long; Didn't Read | The one-line summary field in the DZone article metadata. |
| UI | User Interface | Swagger UI (`/swagger-ui.html`) and the static dashboard. |
| URL | Uniform Resource Locator | Endpoint paths and JDBC URLs (`jdbc:h2:file:./data/flywaydb`). |
| VS | Visual Studio | As in VS Code, the editor. |
| XML | Extensible Markup Language | Liquibase changelog format for `001`, `004`, `005` and `006`. |
| XSS | Cross-Site Scripting | What the dashboard's `esc()` helper prevents. |
| YAML | YAML Ain't Markup Language | The master changelog, changeset `002` and `application.yml`. |

Names that look like acronyms but are not: **H2** (the H2 Database Engine), **JaCoCo** (Java Code Coverage
library), **Marp** (Markdown Presentation Ecosystem tool name), **OpenAPI** (specification name).
