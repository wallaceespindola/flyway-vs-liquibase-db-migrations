# Class Model Diagram — DTO / Record Model

Every REST endpoint returns `ApiResponse<T>`. `T` is one of the records below. All of them are Java records with
component names taken verbatim from the source.

Source: `src/main/java/com/wallaceespindola/dbmigration/dto`, `domain/MigrationEngine.java`

```mermaid
classDiagram
    direction TB

    class ApiResponse~T~ {
        <<record>>
        +boolean success
        +String message
        +T data
        +Instant timestamp
        +ok(T data, String message) ApiResponse$
        +error(String message) ApiResponse$
    }

    class ComparisonReport {
        <<record>>
        +MigrationStatusReport flyway
        +MigrationStatusReport liquibase
        +SchemaSnapshot flywaySchema
        +SchemaSnapshot liquibaseSchema
        +boolean schemasEquivalent
        +List~String~ schemaDifferences
        +List~FeatureComparison~ featureMatrix
    }

    class MigrationStatusReport {
        <<record>>
        +MigrationEngine engine
        +String engineDisplayName
        +String historyTable
        +int appliedCount
        +int pendingCount
        +Instant lastAppliedAt
        +boolean upToDate
        +List~AppliedMigration~ migrations
    }

    class AppliedMigration {
        <<record>>
        +String identifier
        +String description
        +String script
        +String type
        +String author
        +String checksum
        +Instant appliedAt
        +Long executionTimeMs
        +String status
    }

    class SchemaSnapshot {
        <<record>>
        +MigrationEngine engine
        +List~String~ tables
        +List~String~ views
        +List~String~ columns
        +List~String~ indexes
        +List~String~ bookkeepingTables
    }

    class ProductView {
        <<record>>
        +Long productId
        +String sku
        +String productName
        +BigDecimal price
        +Integer stockQuantity
        +Boolean active
        +Long categoryId
        +String categoryName
    }

    class FeatureComparison {
        <<record>>
        +String feature
        +String flyway
        +String liquibase
        +String edge
    }

    class MigrationEngine {
        <<enumeration>>
        FLYWAY
        LIQUIBASE
        -String displayName
        -String historyTable
        -String model
        +displayName() String
        +historyTable() String
        +model() String
        +from(String value) MigrationEngine$
    }

    ApiResponse ..> ComparisonReport : data of GET /api/v1/comparison
    ApiResponse ..> MigrationStatusReport : data of GET /api/v1/migrations/:engine
    ApiResponse ..> SchemaSnapshot : data of GET /api/v1/migrations/:engine/schema
    ApiResponse ..> ProductView : data of GET /api/v1/catalog/:engine
    ApiResponse ..> FeatureComparison : data of GET /api/v1/comparison/features

    ComparisonReport *-- MigrationStatusReport : flyway and liquibase
    ComparisonReport *-- SchemaSnapshot : flywaySchema and liquibaseSchema
    ComparisonReport *-- FeatureComparison : featureMatrix, 18 rows
    MigrationStatusReport *-- AppliedMigration : migrations
    MigrationStatusReport --> MigrationEngine : engine
    SchemaSnapshot --> MigrationEngine : engine
```

Normalisation decisions visible in the model:

| Field | Flyway value | Liquibase value |
|---|---|---|
| `AppliedMigration.identifier` | `V1`, `V2` ... or `R__` plus description for repeatables | `id::author`, e.g. `001-create-category-table::wallaceespindola` |
| `AppliedMigration.author` | `n/a` — Flyway records no authorship | the mandatory changeset `author` attribute |
| `AppliedMigration.checksum` | Flyway CRC32, rendered as `String` | the `MD5SUM` column |
| `AppliedMigration.executionTimeMs` | milliseconds from `MigrationInfo.getExecutionTime()` | `null` — Liquibase persists no per-changeset duration |
| `AppliedMigration.type` / `status` | `MigrationType` name / `MigrationState` display name | `EXECTYPE` in both fields |
| `AppliedMigration.script` | migration filename from `MigrationInfo.getScript()` | the `FILENAME` column |
| `MigrationStatusReport.historyTable` | `flyway_schema_history` | `DATABASECHANGELOG` |
| `MigrationStatusReport.pendingCount` | counted from `info().all()` where `installedOn` is null | always `0` — detecting pending changesets would require a full changelog parse |

`SchemaSnapshot.columns` entries are formatted `TABLE.COLUMN:TYPE`. `SchemaSnapshot.indexes` is reported but
deliberately excluded from the diff, because H2 auto-generates constraint-backing index names that legitimately
differ between the two engines.

`FeatureComparison.edge` is one of the string literals `FLYWAY`, `LIQUIBASE` or `TIE`. The 18 rows live in
`service/FeatureMatrix.java` as an immutable static list.

Author: Wallace Espindola — [github.com/wallaceespindola](https://github.com/wallaceespindola/) ·
[linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)
