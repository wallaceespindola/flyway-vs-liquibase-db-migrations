# Component Diagram — C4 Container / Component View

One Spring Boot process serves the REST API and owns both migration engines. Each engine has its own
`DataSource`, its own `JdbcTemplate` and its own H2 file database. Nothing is shared between the two sides except
the JVM and the service classes that read from both.

Source: `config/`, `controller/`, `service/`, `application.yml`

```mermaid
flowchart TB
    user["Developer / Reviewer"]

    subgraph browser["Browser"]
        dashboard["Static dashboard<br/>src/main/resources/static"]
        swagger["Swagger UI<br/>/swagger-ui.html"]
        h2c["H2 Console<br/>/h2-console"]
    end

    subgraph app["Spring Boot 3.4.2 process — JVM 21 — embedded Tomcat :8080"]
        subgraph web["Web layer @RestController"]
            cmpCtrl["ComparisonController<br/>GET /api/v1/comparison<br/>GET /api/v1/comparison/features"]
            migCtrl["MigrationController<br/>GET /api/v1/migrations<br/>GET /api/v1/migrations/engines<br/>GET /api/v1/migrations/{engine}<br/>GET /api/v1/migrations/{engine}/schema"]
            catCtrl["CatalogController<br/>GET /api/v1/catalog/{engine}"]
            healthCtrl["HealthController<br/>GET /api/v1/health"]
            advice["GlobalExceptionHandler<br/>@RestControllerAdvice"]
            actuator["Actuator<br/>/actuator/health, info, metrics"]
        end

        subgraph svc["Service layer @Service"]
            cmpSvc["ComparisonService"]
            schemaSvc["SchemaInspectionService"]
            fwHist["FlywayHistoryService<br/>MigrationHistoryProvider"]
            lbHist["LiquibaseHistoryService<br/>MigrationHistoryProvider"]
            matrix["FeatureMatrix<br/>18 static rows"]
        end

        subgraph cfg["Configuration @Configuration"]
            fwCfg["FlywayConfig"]
            lbCfg["LiquibaseConfig"]
        end

        subgraph eng["Migration engines"]
            flyway["Flyway<br/>initMethod=migrate<br/>locations=classpath:db/migration"]
            liquibase["SpringLiquibase<br/>changeLog=db.changelog-master.yaml<br/>contexts=demo"]
        end

        subgraph dao["Data access"]
            fwJdbc["flywayJdbcTemplate<br/>@Primary @DependsOn flyway"]
            lbJdbc["liquibaseJdbcTemplate<br/>@DependsOn liquibase"]
        end
    end

    subgraph res["Classpath resources"]
        fwScripts["db/migration<br/>V1..V5 + R__ — 6 scripts"]
        lbChangelogs["db/changelog<br/>master YAML + 001..006<br/>7 changesets"]
    end

    fwDb[("H2 file DB — ./data/flywaydb<br/>category, product, product_audit<br/>v_product_catalog<br/>flyway_schema_history")]
    lbDb[("H2 file DB — ./data/liquibasedb<br/>category, product, product_audit<br/>v_product_catalog<br/>DATABASECHANGELOG + LOCK")]

    user --> browser
    dashboard --> cmpCtrl
    browser -->|HTTP JSON| cmpCtrl
    browser -->|HTTP JSON| migCtrl
    browser -->|HTTP JSON| catCtrl
    browser -->|HTTP JSON| healthCtrl
    swagger -.->|OpenAPI 3 via springdoc| cmpCtrl
    h2c -.->|JDBC, dev only| fwDb
    h2c -.->|JDBC, dev only| lbDb

    cmpCtrl --> cmpSvc
    migCtrl --> cmpSvc
    healthCtrl --> cmpSvc
    catCtrl --> schemaSvc
    cmpCtrl --> matrix
    advice -.->|wraps errors in ApiResponse| web

    cmpSvc --> fwHist
    cmpSvc --> lbHist
    cmpSvc --> schemaSvc
    cmpSvc --> matrix

    fwHist -->|"info().all()"| flyway
    lbHist -->|SELECT FROM DATABASECHANGELOG| lbJdbc
    schemaSvc -->|INFORMATION_SCHEMA + v_product_catalog| fwJdbc
    schemaSvc -->|INFORMATION_SCHEMA + v_product_catalog| lbJdbc

    fwCfg -.->|builds| flyway
    fwCfg -.->|builds| fwJdbc
    lbCfg -.->|builds| liquibase
    lbCfg -.->|builds| lbJdbc

    flyway -->|reads| fwScripts
    liquibase -->|reads| lbChangelogs
    flyway -->|DDL/DML + history rows| fwDb
    liquibase -->|DDL/DML + changelog rows| lbDb

    fwJdbc -->|JDBC| fwDb
    lbJdbc -->|JDBC| lbDb
```

Key structural points:

- `DataSourceAutoConfiguration` is excluded in `application.yml`. Both `DataSource` beans are declared explicitly,
  so there is no ambiguity about which engine touches which database.
- The Flyway side is `@Primary` (`flywayDataSourceProperties`, `flywayDataSource`, `flywayJdbcTemplate`); the
  Liquibase side is resolved by `@Qualifier`.
- `SchemaInspectionService` is the only component holding both `JdbcTemplate`s, in a
  `Map<MigrationEngine, JdbcTemplate>`.
- The static dashboard lives in `src/main/resources/static` (`index.html`, `css/styles.css`, `js/app.js`) and is
  served by the same Spring Boot process on port 8080. It calls `/api/v1/comparison` and `/api/v1/catalog/{engine}`.
  Swagger UI is available alongside it at `/swagger-ui.html`.

Author: Wallace Espindola — [github.com/wallaceespindola](https://github.com/wallaceespindola/) ·
[linkedin.com/in/wallaceespindola](https://www.linkedin.com/in/wallaceespindola/)
