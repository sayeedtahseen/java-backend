package com.sayeed.lender_training_portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;

import com.sayeed.lender_training_portal.auth.JwtProperties;
import com.sayeed.lender_training_portal.support.IntegrationTestBase;

/**
 * Replaces the Initializr-generated contextLoads(). On its own that test needed a database and had none.
 * Extending IntegrationTestBase gives it the Testcontainers Postgres and lets it share the cached context
 * with the other integration tests, so it adds almost no time.
 */
class LenderTrainingPortalApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoads_againstTestcontainersPostgres_withTestOnlyConfig(
            @Autowired DataSource dataSource, @Autowired JwtProperties jwt, @Autowired ApplicationContext context)
            throws Exception {

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        // src/test/resources/application.yml overrides the secret...
        assertThat(jwt.secret()).isEqualTo("dGVzdC1vbmx5LWp3dC1zZWNyZXQtbmV2ZXItdXNlLWluLXByb2R1Y3Rpb24h");
        // ...while everything else still comes from src/main/resources/application.yaml.
        assertThat(jwt.issuer()).isEqualTo("lender-training-portal");
        // Spring ignores unknown keys silently; a typo such as "virtual.enable" would leave this null.
        assertThat(context.getEnvironment().getProperty("spring.threads.virtual.enabled", Boolean.class)).isTrue();
        // The profile-gated demo runners (n1-demo, service-demo) must not run during tests.
        assertThat(context.getBeansOfType(CommandLineRunner.class)).isEmpty();
    }
}
