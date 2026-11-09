package kz.edu.biletflow.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the whole application on a random port against a throwaway Postgres in Docker.
 * The container is shared by all test classes and removed by Testcontainers when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    // Fills the ${...} placeholders of application.yaml, so tests need no environment variables.
    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("JWT_SECRET", () -> "integration-test-only-secret-".repeat(4));
        registry.add("SEED_DATA", () -> "false");
        // Tests deliver queued emails themselves, so the background dispatcher must stay out of the way.
        registry.add("app.mail.dispatch-delay-ms", () -> "3600000");
    }
}
