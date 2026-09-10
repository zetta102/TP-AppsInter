package tp.appsinter.cine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de la aplicación.
 *
 * <p>La planificación está habilitada para las tareas de mantenimiento —barrido de bloqueos de
 * butacas y de tokens de refresco ya inertes—, que son higiene y nunca condicionan lo que la API
 * responde.
 */
@SpringBootApplication
@EnableScheduling
public class CineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineApplication.class, args);
    }

}
