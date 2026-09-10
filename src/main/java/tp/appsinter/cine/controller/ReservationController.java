package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.request.CreateReservationDTO;
import tp.appsinter.cine.model.dto.request.ReservationFilterDTO;
import tp.appsinter.cine.model.dto.response.CreateReservationResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.service.ReservationService;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations")
public class ReservationController {

    private static final Map<String, String> SORT_ALIASES = Map.of(
            "screening.startsAt", "movieScreening.startsAt",
            "screeningStartsAt", "movieScreening.startsAt",
            "userEmail", "user.email");

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
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return reservationService.findMine(authentication.getName(), filter, translateSort(pageable));
    }

    /**
     * Vista de administración sobre todos los clientes.
     */
    @GetMapping("/all")
    public PageResponseDTO<CreateReservationResponseDTO> findAll(
            Authentication authentication,
            ReservationFilterDTO filter,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        if (!isAdmin(authentication)) {
            throw new AccessDeniedException("Only administrators can list every reservation");
        }
        return reservationService.findAll(filter, translateSort(pageable));
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

    /**
     * Traduce los nombres de ordenamiento publicados a los atributos reales de la entidad.
     *
     * <p>El contrato expone {@code screening.startsAt} porque es como el cliente llama a la función;
     * la asociación en el modelo se llama {@code movieScreening}. Sin esta traducción, ordenar por
     * «lo que viene» fallaría con un error de propiedad desconocida.
     */
    private Pageable translateSort(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return pageable;
        }
        Sort translated = Sort.by(pageable.getSort().stream()
                .map(order -> order.withProperty(SORT_ALIASES.getOrDefault(order.getProperty(), order.getProperty())))
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translated);
    }
}
