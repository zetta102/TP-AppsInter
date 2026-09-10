package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tp.appsinter.cine.model.dto.request.BulkSeatRequestDTO;
import tp.appsinter.cine.model.dto.request.GenerateSeatsRequestDTO;
import tp.appsinter.cine.model.dto.response.SeatLayoutResponseDTO;
import tp.appsinter.cine.service.SeatService;

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
                                            @Valid @RequestBody BulkSeatRequestDTO request) {
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
