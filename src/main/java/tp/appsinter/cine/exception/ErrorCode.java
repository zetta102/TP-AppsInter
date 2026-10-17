package tp.appsinter.cine.exception;

import org.springframework.http.HttpStatus;

/**
 * Registro estable de códigos de error de la API.
 *
 * <p>El valor del código nunca se traduce: es la única parte de una respuesta {@code problem+json}
 * sobre la que un cliente puede ramificar con seguridad. El campo {@code detail} sigue siendo texto
 * humano y localizable, y por lo tanto no debe usarse para tomar decisiones.
 */
public enum ErrorCode {

    /**
     * Falló la validación de Bean Validation. El contexto viaja en la propiedad {@code errors}.
     */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    /**
     * Correo o contraseña incorrectos.
     */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

    /**
     * El token de acceso expiró y debe renovarse.
     */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),

    /**
     * El token de refresco ya fue consumido o su familia fue revocada.
     */
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED),

    /**
     * El rol del usuario autenticado no alcanza para la operación.
     */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /**
     * El recurso no existe o no pertenece a quien lo solicita.
     */
    NOT_FOUND(HttpStatus.NOT_FOUND),

    /**
     * Ya existe una cuenta registrada con ese correo.
     */
    EMAIL_TAKEN(HttpStatus.CONFLICT),

    /**
     * Nombre duplicado de sala o producto, o posición de butaca ya ocupada en la sala.
     */
    NAME_TAKEN(HttpStatus.CONFLICT),

    /**
     * Una o más butacas ya fueron vendidas o están retenidas por otra persona.
     */
    SEATS_UNAVAILABLE(HttpStatus.CONFLICT),

    /**
     * No hay stock suficiente del producto solicitado.
     *
     * <p>Se publica como {@code 422}, no como {@code 409}: el pedido no es reintentable sin
     * modificarlo, a diferencia de un conflicto de butacas. Esto conserva la cadena de validación
     * documentada en {@code frontend-spec.md} §9.
     */
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * La sala ya tiene una función que se superpone con el horario pedido.
     */
    SCREENING_OVERLAP(HttpStatus.CONFLICT),

    /**
     * El borrado está bloqueado porque otros registros referencian el recurso.
     */
    RESOURCE_IN_USE(HttpStatus.CONFLICT),

    /**
     * Conflicto de {@code @Version}: el recurso fue modificado de forma concurrente.
     */
    OPTIMISTIC_LOCK(HttpStatus.CONFLICT),

    /**
     * La función ya comenzó y no admite más reservas.
     */
    SCREENING_STARTED(HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * La reserva ya había sido cancelada.
     */
    ALREADY_CANCELLED(HttpStatus.UNPROCESSABLE_CONTENT),

    // --- Extensiones al registro mínimo de backend-spec.md §8 ---

    /**
     * Petición mal formada que no proviene de Bean Validation.
     */
    BAD_REQUEST(HttpStatus.BAD_REQUEST),

    /**
     * El parámetro {@code sort} nombra una propiedad que la entidad no tiene.
     */
    INVALID_SORT_PROPERTY(HttpStatus.BAD_REQUEST),

    /**
     * Conflicto genérico, normalmente una restricción de base de datos sin traducción específica.
     */
    CONFLICT(HttpStatus.CONFLICT),

    /**
     * Alguna butaca pertenece a una sala distinta de la de la función.
     */
    SEATS_WRONG_ROOM(HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * Extensión. El cambio de rol dejaría al sistema sin ningún administrador, o un administrador
     * intenta quitarse a sí mismo el rol. En ambos casos el sistema quedaría sin quien lo administre.
     */
    LAST_ADMIN(HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * Extensión. La contraseña vigente no coincide al intentar cambiarla. No es un {@code 401}: la
     * sesión sigue siendo válida y el cliente no debe tratarlo como un cierre de sesión.
     */
    WRONG_PASSWORD(HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * Extensión. El recurso está archivado: sigue existiendo y resolviéndose por identificador, pero
     * no admite operaciones nuevas (bloquear, presupuestar o comprar butacas de una función
     * archivada, pedir un producto archivado, programar una función de una película o sala
     * archivada). El contexto lleva {@code resource} e {@code id}.
     */
    RESOURCE_ARCHIVED(HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * Extensión. El recurso existe pero no admite el método HTTP pedido.
     */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    /**
     * Extensión. El servidor no puede producir ninguno de los formatos que acepta el cliente.
     */
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),

    /**
     * Extensión. El cuerpo llega en un formato que el endpoint no acepta.
     */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    /**
     * Extensión. Falla inesperada del servidor; el detalle nunca expone la causa interna.
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    /**
     * Estado HTTP con el que se publica este código.
     */
    public HttpStatus status() {
        return status;
    }
}
