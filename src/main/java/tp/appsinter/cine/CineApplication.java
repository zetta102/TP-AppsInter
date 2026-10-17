package tp.appsinter.cine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de la aplicación.
 *
 * <p>La planificación está habilitada para las tareas de mantenimiento —barrido de bloqueos de
 * butacas y de tokens de refresco ya inertes—, que son higiene y nunca condicionan lo que la API
 * responde. La ejecución asíncrona reparte los avisos del canal de butacas sin demorar
 * la petición que los provocó.
 *
 * <p>Los métodos resilientes ({@code @Retryable}) envuelven por fuera a {@code @Transactional}: cada
 * reintento abre una transacción nueva y relee el estado, en lugar de repetir dentro de una que ya
 * quedó marcada para deshacerse. Eso depende del orden por defecto: el post-procesador de reintentos
 * corre último y antepone su consejo a los del proxy transaccional ya creado. Adelantarlo haría que
 * creara un proxy propio por debajo del transaccional.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableResilientMethods
public class CineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineApplication.class, args);
    }

}
