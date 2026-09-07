package com.rag.common.testcontainers;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers bootstrap for integration tests that must exercise the
 * real PostgreSQL / pgvector path rather than an in-memory substitute. The
 * container is started once per JVM and reused across all consuming modules.
 * <p>
 * Consumers depend on rag-common's test-jar and annotate their test with
 * {@code @ContextConfiguration(initializers = PostgresContainerConfig.class)}
 * (or {@code @SpringBootTest} combined with {@code @ContextConfiguration}, see
 * {@link SpringBootTest}). Because initializers run before the application
 * context is created, the datasource properties always point at the container.
 */
public class PostgresContainerConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String IMAGE = "pgvector/pgvector:pg16";
    private static final String USERNAME = "rag";
    private static final String PASSWORD = "rag";
    private static final String DATABASE = "rag";

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName(DATABASE)
                    .withUsername(USERNAME)
                    .withPassword(PASSWORD);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        POSTGRES.start();
        TestPropertyValues.of(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword())
                .applyTo(context.getEnvironment());
    }
}