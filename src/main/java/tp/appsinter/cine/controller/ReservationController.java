package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.request.CreateReservationDTO;
import tp.appsinter.cine.model.dto.request.ReservationFilterDTO;
import tp.appsinter.cine.model.dto.response.CreateReservationResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.service.ReservationService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations")
public class ReservationController {

    /**
     * El contrato expone {@code screening.startsAt} porque es como el cliente llama a la función; la
     * asociación en el modelo se llama {@code movieScreening}. Sin esta traducción, ordenar por «lo
     * que viene» fallaría con un error de propiedad desconocida.
     */
    private static final SortWhitelist SORTABLE = SortWhitelist.of("createdAt", "status", "totalPrice", "cancelledAt")
            .alias("screening.startsAt", "movieScreening.startsAt")
            .alias("screeningStartsAt", "movieScreening.startsAt")
            .alias("userEmail", "user.email");

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<CreateReservationResponseDTO> create(
            Authentication authentication,
            @Valid @RequestBody CreateReservationDTO request) {
        CreateReservationResponseDTO created = reservationService.createReservation(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/reservations/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<CreateReservationResponseDTO> findMine(
            Authentication authentication,
            ReservationFilterDTO filter,
            @ParameterObject Pageable pageable) {
        return reservationService.findMine(authentication.getName(), filter, SORTABLE.apply(pageable));
    }

    /**
     * Vista de administración sobre todos los clientes. El rol se exige en {@code SecurityConfig},
     * junto con el resto de las reglas de acceso.
     */
    @GetMapping("/all")
    public PageResponseDTO<CreateReservationResponseDTO> findAll(
            ReservationFilterDTO filter,
            @ParameterObject Pageable pageable) {
        return reservationService.findAll(filter, SORTABLE.apply(pageable));
    }

    @GetMapping("/{uuid}")
    public CreateReservationResponseDTO findById(Authentication authentication, @PathVariable UUID uuid) {
        return reservationService.findById(uuid, authentication.getName(), isAdmin(authentication));
    }

    /**
     * Cancela una reserva. La reserva se conserva con sus entradas y su confitería; lo único que
     * cambia es su estado.
     */
    @PostMapping("/{uuid}/cancellation")
    public CreateReservationResponseDTO cancel(Authentication authentication, @PathVariable UUID uuid) {
        return reservationService.cancelReservation(uuid, authentication.getName(), isAdmin(authentication));
    }

    /**
     * Alias histórico de {@link #cancel}. Se mantiene por compatibilidad aunque el verbo ya no
     * describa lo que ocurre: nada se borra.
     */
    @DeleteMapping("/{uuid}")
    public CreateReservationResponseDTO cancelByDelete(Authentication authentication, @PathVariable UUID uuid) {
        return cancel(authentication, uuid);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
