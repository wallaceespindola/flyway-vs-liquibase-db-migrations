package com.wallaceespindola.dbmigration.config;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the Flyway side of the comparison: its own H2 database, its own {@link Flyway} instance and
 * a {@link JdbcTemplate} that is guaranteed to be usable only after migrations have run.
 *
 * <p>Flyway's bootstrap is deliberately explicit here. In three lines it takes a {@code DataSource},
 * a filesystem/classpath location and runs every versioned script it finds in order. That brevity is
 * the whole Flyway value proposition, and it is what {@link LiquibaseConfig} is contrasted against.
 *
 * @author Wallace Espindola
 */
@Slf4j
@Configuration
public class FlywayConfig {

    public static final String DATA_SOURCE = "flywayDataSource";
    public static final String JDBC_TEMPLATE = "flywayJdbcTemplate";

    @Value("${app.migration.flyway.locations}")
    private String locations;

    @Value("${app.migration.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.flyway")
    public DataSourceProperties flywayDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(DATA_SOURCE)
    @Primary
    public DataSource flywayDataSource(
            @Qualifier("flywayDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    /**
     * Flyway migrates on bean initialisation. Anything that reads the Flyway database must therefore
     * declare a dependency on this bean, which is what the {@code @DependsOn} below guarantees.
     */
    @Bean(name = "flyway", initMethod = "migrate")
    public Flyway flyway(@Qualifier(DATA_SOURCE) DataSource dataSource) {
        log.info("Configuring Flyway with locations={} baselineOnMigrate={}", locations, baselineOnMigrate);
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(baselineOnMigrate)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();
    }

    @Bean(JDBC_TEMPLATE)
    @Primary
    @DependsOn("flyway")
    public JdbcTemplate flywayJdbcTemplate(@Qualifier(DATA_SOURCE) DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
