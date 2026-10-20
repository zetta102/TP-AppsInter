package tp.appsinter.cine.room;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tp.appsinter.cine.room.dto.request.BulkSeatsRequestDTO;
import tp.appsinter.cine.room.dto.request.GenerateSeatsRequestDTO;
import tp.appsinter.cine.room.dto.response.SeatLayoutResponseDTO;

import java.net.URI;
import java.util.UUID;

/**
 * Alta masiva de butacas de una sala.
 *
 * <p>Vive fuera de {@link SeatController} porque sus rutas llevan dos puntos y cuelgan de la sala,
 * no de {@code /api/seats}. Ambas devuelven la distribución completa resultante, así que el cliente
 * no necesita releer la sala para dibujarla; {@code Location} apunta a esa misma distribución.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Seats")
public class RoomSeatBulkController {

    private final SeatService seatService;

    /**
     * Crea un lote de butacas en una sola transacción.
     *
     * <p>Acepta también {@code /api/rooms/{uuid}/seats/bulk} por los clientes que escapan mal los
     * dos puntos.
     */
    @PostMapping({"/api/rooms/{uuid}/seats:bulk", "/api/rooms/{uuid}/seats/bulk"})
    public ResponseEntity<SeatLayoutResponseDTO> createBulk(@PathVariable UUID uuid,
                                                            @Valid @RequestBody BulkSeatsRequestDTO request) {
        return created(uuid, seatService.createBulk(uuid, request));
    }

    /**
     * Genera una grilla rectangular de butacas a partir de sus dimensiones.
     */
    @PostMapping({"/api/rooms/{uuid}/seats:generate", "/api/rooms/{uuid}/seats/generate"})
    public ResponseEntity<SeatLayoutResponseDTO> generate(@PathVariable UUID uuid,
                                                          @Valid @RequestBody GenerateSeatsRequestDTO request) {
        return created(uuid, seatService.generate(uuid, request));
    }

    private static ResponseEntity<SeatLayoutResponseDTO> created(UUID roomUuid, SeatLayoutResponseDTO layout) {
        return ResponseEntity.created(URI.create("/api/rooms/%s/layout".formatted(roomUuid))).body(layout);
    }
}
