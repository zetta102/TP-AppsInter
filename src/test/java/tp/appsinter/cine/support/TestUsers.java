package tp.appsinter.cine.support;

import tp.appsinter.cine.user.Role;
import tp.appsinter.cine.user.UserService;
import tp.appsinter.cine.user.dto.request.RegisterRequestDTO;

/**
 * Las cuentas con que trabajan las pruebas de la API: dos clientes y un administrador, todos con la
 * misma contraseña.
 */
public final class TestUsers {

    public static final String CUSTOMER = "ada@example.com";
    public static final String OTHER_CUSTOMER = "charles@example.com";
    public static final String ADMIN = "admin@example.com";
    public static final String PASSWORD = "supersecret";

    private TestUsers() {
    }

    public static void createAll(UserService userService) {
        userService.createUser(new RegisterRequestDTO("Ada", "Lovelace", CUSTOMER, PASSWORD), Role.CUSTOMER);
        userService.createUser(new RegisterRequestDTO("Charles", "Babbage", OTHER_CUSTOMER, PASSWORD), Role.CUSTOMER);
        userService.createUser(new RegisterRequestDTO("Root", "Admin", ADMIN, PASSWORD), Role.ADMIN);
    }
}
