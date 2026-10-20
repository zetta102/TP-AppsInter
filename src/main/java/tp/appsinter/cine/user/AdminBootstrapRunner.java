package tp.appsinter.cine.user;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.user.dto.request.RegisterRequestDTO;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * El registro siempre crea clientes, así que sin esta semilla nunca podría existir un administrador.
 *
 * <p>Sólo siembra si no queda ningún administrador activo: uno dado de baja no puede iniciar sesión y
 * no cuenta. Los datos sembrados cumplen las mismas reglas que un alta por la API; si no, el arranque
 * falla en lugar de crear una cuenta que el propio sistema rechazaría.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final UserService userService;
    private final Validator validator;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.countByRoleAndDeletedAtIsNull(Role.ADMIN) > 0) {
            log.debug("An administrator already exists, skipping bootstrap");
            return;
        }
        if (!adminProperties.configured()) {
            log.warn("No administrator exists and cine.admin.email/cine.admin.password are not set. "
                    + "Set CINE_ADMIN_EMAIL and CINE_ADMIN_PASSWORD to seed the first administrator.");
            return;
        }

        var request = new RegisterRequestDTO(
                adminProperties.firstName(),
                adminProperties.lastName(),
                adminProperties.email(),
                adminProperties.password());
        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("The configured administrator is invalid: " + violations.stream()
                    .map(violation -> "cine.admin." + violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", ")));
        }
        var created = userService.createUser(request, Role.ADMIN);
        log.info("Bootstrapped administrator {}", created.email());
    }
}
