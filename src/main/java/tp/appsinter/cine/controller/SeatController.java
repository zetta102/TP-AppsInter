package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.request.CreateSeatRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateSeatResponseDTO;
import tp.appsinter.cine.service.SeatService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
@Tag(name = "Seats")
public class SeatController {

    private final SeatService seatService;

    @PostMapping
    public ResponseEntity<CreateSeatResponseDTO> create(@Valid @RequestBody CreateSeatRequestDTO request) {
        CreateSeatResponseDTO created = seatService.create(request);
        return ResponseEntity.created(URI.create("/api/seats/" + created.uuid())).body(created);
    }

    @GetMapping("/{uuid}")
    public CreateSeatResponseDTO findById(@PathVariable UUID uuid) {
        return seatService.findById(uuid);
    }

    @PutMapping("/{uuid}")
    public CreateSeatResponseDTO update(@PathVariable UUID uuid,
                                        @Valid @RequestBody CreateSeatRequestDTO request) {
        return seatService.update(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        seatService.delete(uuid);
    }
}
