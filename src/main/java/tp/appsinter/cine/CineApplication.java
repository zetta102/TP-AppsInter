package tp.appsinter.cine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de la aplicación.
 *
 * <p>La planificación está habilitada para las tareas de mantenimiento —barrido de bloqueos de
 * butacas y de tokens de refresco ya inertes—, que son higiene y nunca condicionan lo que la API
 * responde. La ejecución asíncrona reparte los avisos del canal de butacas sin demorar
 * la petición que los provocó.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class CineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineApplication.class, args);
    }

}
