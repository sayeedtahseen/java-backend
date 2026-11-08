package com.sayeed.lender_training_portal.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One real PostgreSQL for the tests.
 *
 * <p>The container is a Spring bean, so its lifecycle follows the application context: Spring starts it
 * when the context is built and stops it when the context is closed. {@code @ServiceConnection} makes
 * Boot read the container's JDBC URL, user and password and build the DataSource from them, so no
 * {@code spring.datasource.*} properties are needed. Flyway then runs the real V1__init.sql against it.
 *
 * <p>Every test class that imports this (with otherwise identical configuration) shares one cached
 * context and therefore one container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Pinned major version: compose.yaml uses postgres:latest, but tests should not change under us.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }
}
