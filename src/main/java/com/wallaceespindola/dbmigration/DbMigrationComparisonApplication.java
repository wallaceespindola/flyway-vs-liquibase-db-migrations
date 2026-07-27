package com.wallaceespindola.dbmigration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Flyway vs Liquibase comparison application.
 *
 * <p>The application boots two independent H2 databases with an identical logical schema: one
 * migrated by Flyway, the other by Liquibase. REST endpoints and a static dashboard expose the
 * state of both so the two tools can be compared side by side on real output rather than prose.
 *
 * @author Wallace Espindola
 */
@SpringBootApplication
public class DbMigrationComparisonApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbMigrationComparisonApplication.class, args);
    }
}
