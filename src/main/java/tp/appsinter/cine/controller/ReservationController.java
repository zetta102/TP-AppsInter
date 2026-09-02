package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.request.CreateReservationDTO;
import tp.appsinter.cine.model.dto.response.CreateReservationResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.utils.ReservationStatus;
import tp.appsinter.cine.service.ReservationService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations")
public class ReservationController {

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
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return reservationService.findMine(authentication.getName(), pageable);
    }

    /**
     * Admin-wide view across all customers.
     */
    @GetMapping("/all")
    public PageResponseDTO<CreateReservationResponseDTO> findAll(
            Authentication authentication,
            @RequestParam(required = false) ReservationStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        if (!isAdmin(authentication)) {
            throw new AccessDeniedException("Only administrators can list every reservation");
        }
        return reservationService.findAll(status, pageable);
    }

    @GetMapping("/{uuid}")
    public CreateReservationResponseDTO findById(Authentication authentication, @PathVariable UUID uuid) {
        return reservationService.findById(uuid, authentication.getName(), isAdmin(authentication));
    }

    @DeleteMapping("/{uuid}")
    public CreateReservationResponseDTO cancel(Authentication authentication, @PathVariable UUID uuid) {
        return reservationService.cancelReservation(uuid, authentication.getName(), isAdmin(authentication));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
