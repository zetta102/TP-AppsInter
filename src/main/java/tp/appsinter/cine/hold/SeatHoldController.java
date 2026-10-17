package tp.appsinter.cine.hold;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.hold.dto.CreateSeatHoldRequestDTO;
import tp.appsinter.cine.hold.dto.SeatHoldResponseDTO;

import java.net.URI;
import java.util.UUID;

/**
 * Bloqueos temporales de butacas.
 *
 * <p>El alta cuelga de la función porque un bloqueo siempre es para una función concreta; la
 * consulta y la liberación cuelgan de {@code /api/holds} porque a partir de ahí el identificador
 * del bloqueo alcanza.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Seat holds")
public class SeatHoldController {

    private final SeatHoldService seatHoldService;

    @PostMapping("/api/screenings/{uuid}/holds")
    public ResponseEntity<SeatHoldResponseDTO> create(Authentication authentication,
                                                      @PathVariable UUID uuid,
                                                      @Valid @RequestBody CreateSeatHoldRequestDTO request) {
        SeatHoldResponseDTO hold = seatHoldService.create(uuid, authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/holds/" + hold.uuid())).body(hold);
    }

    /**
     * Un bloqueo vencido responde 404: ya no aparta nada.
     */
    @GetMapping("/api/holds/{uuid}")
    public SeatHoldResponseDTO findById(Authentication authentication, @PathVariable UUID uuid) {
        return seatHoldService.findById(uuid, authentication.getName());
    }

    /**
     * Reemplaza de una vez las butacas del bloqueo, sin soltar las que siguen elegidas y conservando
     * su vencimiento. Los errores son los mismos que al crearlo; un bloqueo vencido, soltado o ajeno
     * responde 404.
     */
    @PutMapping("/api/holds/{uuid}")
    public SeatHoldResponseDTO replace(Authentication authentication,
                                       @PathVariable UUID uuid,
                                       @Valid @RequestBody CreateSeatHoldRequestDTO request) {
        return seatHoldService.replace(uuid, authentication.getName(), request);
    }

    /**
     * Libera el bloqueo. Es idempotente y siempre responde 204.
     */
    @DeleteMapping("/api/holds/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(Authentication authentication, @PathVariable UUID uuid) {
        seatHoldService.release(uuid, authentication.getName());
    }
}
