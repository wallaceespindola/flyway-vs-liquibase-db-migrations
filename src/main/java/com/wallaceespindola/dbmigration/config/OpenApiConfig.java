package com.wallaceespindola.dbmigration.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI metadata.
 *
 * @author Wallace Espindola
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dbMigrationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flyway vs Liquibase — DB Migrations API")
                        .description("""
                                Side-by-side comparison of Flyway and Liquibase running against two \
                                independent H2 databases with an identical logical schema.""")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Wallace Espindola")
                                .email("wallace.espindola@gmail.com")
                                .url("https://github.com/wallaceespindola/"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.txt")));
    }
}
