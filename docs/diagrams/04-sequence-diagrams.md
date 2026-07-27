# Sequence Diagrams

Two flows: application startup, where both engines migrate their own database during context refresh, and the
`GET /api/v1/comparison` request, which is the endpoint the whole project exists to serve.

Source: `config/FlywayConfig.java`, `config/LiquibaseConfig.java`, `service/ComparisonService.java`,
`controller/ComparisonController.java`

## 1. Startup — both engines migrate during context refresh

```mermaid
sequenceDiagram
    autonumber
    participant JVM
    participant Boot as SpringApplication
    participant FwCfg as FlywayConfig
    participant Flyway
    participant FwDb as H2 ./data/flywaydb
    participant LbCfg as LiquibaseConfig
    participant Liquibase as SpringLiquibase
    participant LbDb as H2 ./data/liquibasedb
    participant Svc as Service beans
    participant Tomcat as Embedded Tomcat

    JVM->>Boot: main(args)
    activate Boot
    Boot->>Boot: load application.yml, exclude DataSourceAutoConfiguration

    rect rgb(235, 245, 255)
    note over Boot,FwDb: Flyway side
    Boot->>FwCfg: flywayDataSourceProperties()
    FwCfg-->>Boot: DataSourceProperties from app.datasource.flyway
    Boot->>FwCfg: flywayDataSource(properties)
    FwCfg-->>Boot: DataSource, @Primary
    Boot->>FwCfg: flyway(dataSource)
    FwCfg->>Flyway: configure locations=classpath:db/migration, baselineOnMigrate=true, validateOnMigrate=true, cleanDisabled=true
    Boot->>Flyway: migrate() via initMethod
    activate Flyway
    Flyway->>FwDb: create or validate flyway_schema_history
    Flyway->>FwDb: V1 create category
    Flyway->>FwDb: V2 create product
    Flyway->>FwDb: V3 seed reference data
    Flyway->>FwDb: V4 add product_audit
    Flyway->>FwDb: V5 add product.active and backfill
    Flyway->>FwDb: R__ create or replace v_product_catalog
    Flyway->>FwDb: insert 6 history rows
    Flyway-->>Boot: MigrateResult
    deactivate Flyway
    Boot->>FwCfg: flywayJdbcTemplate(dataSource), @DependsOn flyway
    FwCfg-->>Boot: JdbcTemplate, @Primary
    end

    rect rgb(240, 255, 240)
    note over Boot,LbDb: Liquibase side
    Boot->>LbCfg: liquibaseDataSourceProperties()
    LbCfg-->>Boot: DataSourceProperties from app.datasource.liquibase
    Boot->>LbCfg: liquibaseDataSource(properties)
    LbCfg-->>Boot: DataSource
    Boot->>LbCfg: liquibase(dataSource)
    LbCfg->>Liquibase: setChangeLog db.changelog-master.yaml, setContexts demo, setDefaultSchema PUBLIC
    Boot->>Liquibase: afterPropertiesSet() via InitializingBean
    activate Liquibase
    Liquibase->>LbDb: create DATABASECHANGELOG and DATABASECHANGELOGLOCK
    Liquibase->>LbDb: acquire changelog lock
    Liquibase->>LbDb: 001 create category, XML
    Liquibase->>LbDb: 002 create product, YAML
    Liquibase->>LbDb: 003 seed reference data, SQL
    Liquibase->>LbDb: 004 add product_audit, preCondition tableExists product
    Liquibase->>LbDb: 005 add product.active
    Liquibase->>LbDb: 005b backfill active=false where stock_quantity=0
    Liquibase->>LbDb: 006 create v_product_catalog, runOnChange
    Liquibase->>LbDb: insert 7 DATABASECHANGELOG rows, release lock
    Liquibase-->>Boot: done
    deactivate Liquibase
    Boot->>LbCfg: liquibaseJdbcTemplate(dataSource), @DependsOn liquibase
    LbCfg-->>Boot: JdbcTemplate
    end

    Boot->>Svc: construct FlywayHistoryService, LiquibaseHistoryService, SchemaInspectionService, ComparisonService
    Svc-->>Boot: beans ready
    Boot->>Tomcat: start on port 8080
    Tomcat-->>Boot: accepting requests
    Boot-->>JVM: context refreshed
    deactivate Boot

    note over Flyway,Liquibase: Both engines run at bean initialisation. A migration failure aborts context refresh, so Tomcat never starts against a half-migrated database.
```

## 2. Request — GET /api/v1/comparison

```mermaid
sequenceDiagram
    autonumber
    actor Browser
    participant Ctrl as ComparisonController
    participant Svc as ComparisonService
    participant Schema as SchemaInspectionService
    participant FwHist as FlywayHistoryService
    participant Flyway
    participant LbHist as LiquibaseHistoryService
    participant Matrix as FeatureMatrix
    participant FwJdbc as flywayJdbcTemplate
    participant LbJdbc as liquibaseJdbcTemplate
    participant FwDb as H2 flywaydb
    participant LbDb as H2 liquibasedb

    Browser->>Ctrl: GET /api/v1/comparison
    activate Ctrl
    Ctrl->>Svc: compare()
    activate Svc

    Svc->>Schema: snapshot(FLYWAY)
    activate Schema
    Schema->>FwJdbc: query INFORMATION_SCHEMA TABLES, VIEWS, COLUMNS, INDEXES
    FwJdbc->>FwDb: SELECT
    FwDb-->>FwJdbc: rows
    Schema-->>Svc: SchemaSnapshot FLYWAY, bookkeeping flyway_schema_history
    deactivate Schema

    Svc->>Schema: snapshot(LIQUIBASE)
    activate Schema
    Schema->>LbJdbc: query INFORMATION_SCHEMA TABLES, VIEWS, COLUMNS, INDEXES
    LbJdbc->>LbDb: SELECT
    LbDb-->>LbJdbc: rows
    Schema-->>Svc: SchemaSnapshot LIQUIBASE, bookkeeping DATABASECHANGELOG and LOCK
    deactivate Schema

    Svc->>Svc: diff over tables, views, columns — indexes excluded as noise

    Svc->>FwHist: status()
    activate FwHist
    FwHist->>Flyway: info().all()
    Flyway-->>FwHist: MigrationInfo array
    FwHist-->>Svc: MigrationStatusReport FLYWAY, 6 applied
    deactivate FwHist

    Svc->>LbHist: status()
    activate LbHist
    LbHist->>LbJdbc: SELECT ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, CONTEXTS, LABELS, DEPLOYMENT_ID FROM DATABASECHANGELOG
    LbJdbc->>LbDb: SELECT
    LbDb-->>LbJdbc: 7 rows
    LbHist-->>Svc: MigrationStatusReport LIQUIBASE, 7 applied
    deactivate LbHist

    Svc->>Matrix: rows()
    Matrix-->>Svc: 18 FeatureComparison rows

    Svc-->>Ctrl: ComparisonReport, schemasEquivalent=true, differences empty
    deactivate Svc
    Ctrl->>Ctrl: ApiResponse.ok(report, "Both engines produced an equivalent business schema")
    Ctrl-->>Browser: 200 OK application/json
    deactivate Ctrl
```

## 3. Failure paths

```mermaid
sequenceDiagram
    autonumber
    actor Browser
    participant Ctrl as Controller
    participant Svc as ComparisonService
    participant Jdbc as JdbcTemplate
    participant Advice as GlobalExceptionHandler

    rect rgb(255, 240, 240)
    note over Browser,Advice: Database unreachable
    Browser->>Ctrl: GET /api/v1/comparison
    Ctrl->>Svc: compare()
    Svc->>Jdbc: SELECT from INFORMATION_SCHEMA
    Jdbc-->>Svc: DataAccessException
    Svc-->>Advice: exception propagates
    Advice-->>Browser: 503 Service Unavailable, ApiResponse.error "Database unavailable ..."
    end

    rect rgb(255, 250, 235)
    note over Browser,Advice: Unknown engine path variable
    Browser->>Ctrl: GET /api/v1/migrations/mysql
    Ctrl->>Ctrl: MigrationEngine.from("mysql")
    Ctrl-->>Advice: IllegalArgumentException
    Advice-->>Browser: 400 Bad Request, ApiResponse.error "Unknown migration engine 'mysql'. Expected one of flyway, liquibase"
    end

    rect rgb(240, 240, 255)
    note over Browser,Advice: Health degraded
    Browser->>Ctrl: GET /api/v1/health
    Ctrl->>Svc: status(FLYWAY) then status(LIQUIBASE)
    Svc-->>Ctrl: RuntimeException for one engine
    Ctrl-->>Browser: 503 Service Unavailable, per-engine status UP or DOWN in the payload
    end
```

Author: Wallace Espindola — [github.com/wallaceespindola](https://github.com/wallaceespindola/) ·
[linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)
