package tp.appsinter.cine.room;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.room.dto.CreateSeatRequestDTO;
import tp.appsinter.cine.room.dto.SeatResponseDTO;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
@Tag(name = "Seats")
public class SeatController {

    private final SeatService seatService;

    @PostMapping
    public ResponseEntity<SeatResponseDTO> create(@Valid @RequestBody CreateSeatRequestDTO request) {
        SeatResponseDTO created = seatService.create(request);
        return ResponseEntity.created(URI.create("/api/seats/" + created.uuid())).body(created);
    }

    @GetMapping("/{uuid}")
    public SeatResponseDTO findById(@PathVariable UUID uuid) {
        return seatService.findById(uuid);
    }

    @PutMapping("/{uuid}")
    public SeatResponseDTO update(@PathVariable UUID uuid,
                                        @Valid @RequestBody CreateSeatRequestDTO request) {
        return seatService.update(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        seatService.delete(uuid);
    }
}
