package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tp.appsinter.cine.model.dto.request.CreateReservationDTO;
import tp.appsinter.cine.model.dto.response.ReservationQuoteResponseDTO;
import tp.appsinter.cine.service.ReservationService;

/**
 * Presupuesto de reservas.
 *
 * <p>Vive fuera de {@link ReservationController} porque su ruta lleva dos puntos y no cuelga de
 * {@code /api/reservations/}: concatenarla con un prefijo de clase produciría
 * {@code /api/reservations/:quote}, que no es lo que pide el contrato.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Reservations")
public class ReservationQuoteController {

    private final ReservationService reservationService;

    /**
     * Calcula el total de un pedido sin crear nada.
     *
     * <p>Acepta también {@code /api/reservations/quote}: la forma con dos puntos es la del contrato,
     * pero hay clientes y proxies que la escapan mal, y ambas rutas atienden lo mismo.
     *
     * <p>El cuerpo es idéntico al del alta de reservas, incluido el nombre {@code products} para la
     * confitería; reusarlo evita que presupuestar y confirmar acepten formas distintas.
     */
    @PostMapping({"/api/reservations:quote", "/api/reservations/quote"})
    public ReservationQuoteResponseDTO quote(@Valid @RequestBody CreateReservationDTO request) {
        return reservationService.quote(request);
    }
}
