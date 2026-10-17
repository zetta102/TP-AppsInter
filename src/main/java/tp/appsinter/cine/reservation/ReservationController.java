package tp.appsinter.cine.reservation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.reservation.dto.CreateReservationRequestDTO;
import tp.appsinter.cine.reservation.dto.ReservationFilterDTO;
import tp.appsinter.cine.reservation.dto.ReservationResponseDTO;
import tp.appsinter.cine.shared.dto.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponseDTO> create(
            Authentication authentication,
            @Valid @RequestBody CreateReservationRequestDTO request) {
        ReservationResponseDTO created = reservationService.createReservation(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/reservations/" + created.uuid())).body(created);
    }

    /**
     * El contrato expone {@code screening.startsAt} porque es como el cliente llama a la función; la
     * asociación en el modelo se llama {@code movieScreening}. Sin esta traducción, ordenar por «lo
     * que viene» fallaría con un error de propiedad desconocida. La misma lista rige para
     * {@link #findAll}.
     */
    @GetMapping
    public PageResponseDTO<ReservationResponseDTO> findMine(
            Authentication authentication,
            @ParameterObject ReservationFilterDTO filter,
            @ParameterObject @AllowedSort(value = {"createdAt", "status", "totalPrice", "cancelledAt"}, aliases = {
                    @AllowedSort.Alias(name = "screening.startsAt", property = "movieScreening.startsAt"),
                    @AllowedSort.Alias(name = "screeningStartsAt", property = "movieScreening.startsAt"),
                    @AllowedSort.Alias(name = "userEmail", property = "user.email")}) Pageable pageable) {
        return reservationService.findMine(authentication.getName(), filter, pageable);
    }

    /**
     * Vista de administración sobre todos los clientes. El rol se exige en {@code SecurityConfig},
     * junto con el resto de las reglas de acceso.
     */
    @GetMapping("/all")
    public PageResponseDTO<ReservationResponseDTO> findAll(
            @ParameterObject ReservationFilterDTO filter,
            @ParameterObject @AllowedSort(value = {"createdAt", "status", "totalPrice", "cancelledAt"}, aliases = {
                    @AllowedSort.Alias(name = "screening.startsAt", property = "movieScreening.startsAt"),
                    @AllowedSort.Alias(name = "screeningStartsAt", property = "movieScreening.startsAt"),
                    @AllowedSort.Alias(name = "userEmail", property = "user.email")}) Pageable pageable) {
        return reservationService.findAll(filter, pageable);
    }

    @GetMapping("/{uuid}")
    public ReservationResponseDTO findById(Authentication authentication, @PathVariable UUID uuid) {
        return reservationService.findById(uuid, authentication.getName(), isAdmin(authentication));
    }

    /**
     * Cancela una reserva. La reserva se conserva con sus entradas y su confitería; lo único que
     * cambia es su estado.
     */
    @PostMapping("/{uuid}/cancellation")
    public ReservationResponseDTO cancel(Authentication authentication, @PathVariable UUID uuid) {
        return reservationService.cancelReservation(uuid, authentication.getName(), isAdmin(authentication));
    }

    /**
     * Alias histórico de {@link #cancel}. Se mantiene por compatibilidad aunque el verbo ya no
     * describa lo que ocurre: nada se borra.
     */
    @DeleteMapping("/{uuid}")
    public ReservationResponseDTO cancelByDelete(Authentication authentication, @PathVariable UUID uuid) {
        return cancel(authentication, uuid);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
