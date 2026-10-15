package tp.appsinter.cine;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Levanta sólo el contenedor de base de datos, para que las pruebas de persistencia no paguen el
 * costo de la pila de observabilidad.
 *
 * <p>El contenedor es único para toda la JVM: las clases de prueba con y sin MockMvc producen
 * contextos de Spring distintos, y sin esto cada uno arrancaba su propio Postgres. Ningún contexto lo
 * detiene al cerrarse ({@code destroyMethod = ""}), porque otro podría seguir usándolo; lo retira
 * Testcontainers al terminar la JVM.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresqlContainer() {
        return POSTGRES;
    }
}
