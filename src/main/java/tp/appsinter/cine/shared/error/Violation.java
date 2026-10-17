package tp.appsinter.cine.shared.error;

import java.util.Map;

/**
 * Violación de una restricción sobre un campo, en forma legible por máquina.
 *
 * <p>Acompaña al mapa {@code errors} de un {@code VALIDATION_FAILED}: aquel trae el mensaje en
 * inglés, éste el nombre de la restricción y sus parámetros para que el cliente pueda armar su
 * propio texto localizado.
 *
 * @param field  campo o parámetro rechazado, con la misma ruta que usa {@code errors}
 * @param code   nombre simple de la restricción ({@code Size}, {@code NotBlank}, {@code HttpUrl}…)
 *               o código de enlace ({@code typeMismatch}) cuando el valor ni siquiera se pudo leer
 * @param params atributos de la restricción ({@code min}, {@code max}…); vacío si no tiene
 */
public record Violation(String field, String code, Map<String, Object> params) {

    public Violation {
        params = Map.copyOf(params);
    }

    public static Violation of(String field, String code) {
        return new Violation(field, code, Map.of());
    }
}
