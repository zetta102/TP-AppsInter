package tp.appsinter.cine.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Archiva o restaura un recurso con {@code PATCH}.
 *
 * <p>Es un objeto y no un booleano suelto para que el mismo {@code PATCH} pueda aceptar más campos
 * en el futuro sin romper a los clientes actuales.
 */
@Schema(name = "ArchiveRequest")
public record ArchiveRequestDTO(@NotNull Boolean archived) {
}
