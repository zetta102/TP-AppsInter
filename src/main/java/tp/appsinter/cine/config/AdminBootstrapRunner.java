package tp.appsinter.cine.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.model.dto.request.CreateUserRequestDTO;
import tp.appsinter.cine.model.utils.Role;
import tp.appsinter.cine.repository.UserRepository;
import tp.appsinter.cine.service.UserService;

/**
 * El registro siempre crea clientes, así que sin esta semilla nunca podría existir un administrador.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.debug("An administrator already exists, skipping bootstrap");
            return;
        }
        if (!adminProperties.configured()) {
            log.warn("No administrator exists and cine.admin.email/cine.admin.password are not set. "
                    + "Set CINE_ADMIN_EMAIL and CINE_ADMIN_PASSWORD to seed the first administrator.");
            return;
        }

        var request = new CreateUserRequestDTO(
                adminProperties.firstName(),
                adminProperties.lastName(),
                adminProperties.email(),
                adminProperties.password());
        var created = userService.createUser(request, Role.ADMIN);
        log.info("Bootstrapped administrator {}", created.email());
    }
}
