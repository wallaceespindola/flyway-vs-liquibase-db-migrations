package com.wallaceespindola.dbmigration.config;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the Liquibase side of the comparison: its own H2 database, a {@link SpringLiquibase} runner
 * and a {@link JdbcTemplate} usable only once the changelog has been applied.
 *
 * <p>Liquibase needs more knobs than Flyway — a changelog entry point, contexts and a default schema
 * — because it models changes as database-agnostic <em>changesets</em> rather than raw SQL files.
 * That extra ceremony buys rollback support, preconditions and multi-format changelogs, which the
 * changelogs under {@code db/changelog} demonstrate.
 *
 * @author Wallace Espindola
 */
@Slf4j
@Configuration
public class LiquibaseConfig {

    public static final String DATA_SOURCE = "liquibaseDataSource";
    public static final String JDBC_TEMPLATE = "liquibaseJdbcTemplate";

    @Value("${app.migration.liquibase.change-log}")
    private String changeLog;

    @Value("${app.migration.liquibase.contexts:}")
    private String contexts;

    @Value("${app.migration.liquibase.default-schema:PUBLIC}")
    private String defaultSchema;

    @Bean
    @ConfigurationProperties("app.datasource.liquibase")
    public DataSourceProperties liquibaseDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(DATA_SOURCE)
    public DataSource liquibaseDataSource(
            @Qualifier("liquibaseDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    /**
     * {@link SpringLiquibase} implements {@code InitializingBean}, so the changelog is applied during
     * bean initialisation — the same lifecycle position as Flyway's {@code migrate()}.
     */
    @Bean("liquibase")
    public SpringLiquibase liquibase(@Qualifier(DATA_SOURCE) DataSource dataSource) {
        log.info("Configuring Liquibase with changeLog={} contexts={}", changeLog, contexts);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.setContexts(contexts);
        liquibase.setDefaultSchema(defaultSchema);
        liquibase.setDropFirst(false);
        liquibase.setShouldRun(true);
        return liquibase;
    }

    @Bean(JDBC_TEMPLATE)
    @DependsOn("liquibase")
    public JdbcTemplate liquibaseJdbcTemplate(@Qualifier(DATA_SOURCE) DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
