package tp.appsinter.cine;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Database-only container setup, so persistence tests do not pay for the observability stack.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresqlContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
