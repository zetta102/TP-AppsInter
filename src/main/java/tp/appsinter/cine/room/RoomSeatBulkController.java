package tp.appsinter.cine.room;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.room.dto.request.BulkSeatsRequestDTO;
import tp.appsinter.cine.room.dto.request.GenerateSeatsRequestDTO;
import tp.appsinter.cine.room.dto.response.SeatLayoutResponseDTO;

import java.util.UUID;

/**
 * Alta masiva de butacas de una sala.
 *
 * <p>Vive fuera de {@link SeatController} porque sus rutas llevan dos puntos y cuelgan de la sala,
 * no de {@code /api/seats}. Ambas devuelven la distribución completa resultante, así que el cliente
 * no necesita releer la sala para dibujarla.
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
    @ResponseStatus(HttpStatus.CREATED)
    public SeatLayoutResponseDTO createBulk(@PathVariable UUID uuid,
                                            @Valid @RequestBody BulkSeatsRequestDTO request) {
        return seatService.createBulk(uuid, request);
    }

    /**
     * Genera una grilla rectangular de butacas a partir de sus dimensiones.
     */
    @PostMapping({"/api/rooms/{uuid}/seats:generate", "/api/rooms/{uuid}/seats/generate"})
    @ResponseStatus(HttpStatus.CREATED)
    public SeatLayoutResponseDTO generate(@PathVariable UUID uuid,
                                          @Valid @RequestBody GenerateSeatsRequestDTO request) {
        return seatService.generate(uuid, request);
    }
}
