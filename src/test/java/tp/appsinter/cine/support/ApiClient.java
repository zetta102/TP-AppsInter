package tp.appsinter.cine.support;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;
import tp.appsinter.cine.auth.dto.request.LoginRequestDTO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que repetía cada prueba de la API: iniciar sesión, serializar cuerpos y leer campos de una
 * respuesta.
 *
 * <p>Los cuerpos se arman con los mismos registros de petición que usa la API y se serializan con el
 * {@link JsonMapper} de la aplicación: así no hay JSON escrito a mano que escapar, y una petición de
 * prueba no puede tener una forma que la API no acepte. Los cuerpos inválidos a propósito siguen
 * escritos a mano.
 */
public final class ApiClient {

    private final MockMvcTester mvc;
    private final JsonMapper jsonMapper;
    /**
     * Iniciar sesión cuesta un bcrypt; dentro de una prueba se reusa el token de cada cuenta.
     */
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public ApiClient(MockMvcTester mvc, JsonMapper jsonMapper) {
        this.mvc = mvc;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Valor del encabezado {@code Authorization} de la cuenta, iniciando sesión la primera vez.
     */
    public String bearer(String email) {
        return tokens.computeIfAbsent(email, this::login);
    }

    private String login(String email) {
        MvcTestResult login = mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequestDTO(email, TestUsers.PASSWORD)))
                .exchange();
        assertThat(login).hasStatusOk();
        return "Bearer " + read(login, "$.token");
    }

    public String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }

    public static String read(MvcTestResult result, String path) {
        return assertThat(result).bodyJson().extractingPath(path).asString().actual();
    }

    /**
     * Olvida los tokens: la base se vacía antes de cada prueba y el reloj puede haber avanzado.
     */
    void forgetTokens() {
        tokens.clear();
    }
}
