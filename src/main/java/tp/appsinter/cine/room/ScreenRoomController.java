package tp.appsinter.cine.room;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.room.dto.CreateScreenRoomRequestDTO;
import tp.appsinter.cine.room.dto.ScreenRoomResponseDTO;
import tp.appsinter.cine.room.dto.SeatLayoutResponseDTO;
import tp.appsinter.cine.room.dto.SeatResponseDTO;
import tp.appsinter.cine.shared.dto.ArchiveRequestDTO;
import tp.appsinter.cine.shared.dto.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Screen rooms")
public class ScreenRoomController {

    private final ScreenRoomService screenRoomService;
    private final SeatService seatService;

    @PostMapping
    public ResponseEntity<ScreenRoomResponseDTO> create(@Valid @RequestBody CreateScreenRoomRequestDTO request) {
        ScreenRoomResponseDTO created = screenRoomService.create(request);
        return ResponseEntity.created(URI.create("/api/rooms/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<ScreenRoomResponseDTO> findAll(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @ParameterObject @AllowedSort({"roomName", "screenRoomType"}) Pageable pageable) {
        return screenRoomService.findAll(includeArchived, pageable);
    }

    @GetMapping("/{uuid}")
    public ScreenRoomResponseDTO findById(@PathVariable UUID uuid) {
        return screenRoomService.findById(uuid);
    }

    @GetMapping("/{uuid}/seats")
    public List<SeatResponseDTO> findSeats(@PathVariable UUID uuid) {
        return seatService.findByScreenRoom(uuid);
    }

    @GetMapping("/{uuid}/layout")
    public SeatLayoutResponseDTO findLayout(@PathVariable UUID uuid) {
        return seatService.findLayout(uuid);
    }

    @PutMapping("/{uuid}")
    public ScreenRoomResponseDTO update(@PathVariable UUID uuid,
                                              @Valid @RequestBody CreateScreenRoomRequestDTO request) {
        return screenRoomService.update(uuid, request);
    }

    /**
     * Archiva o restaura la sala con {@code {"archived": true|false}}. Archivarla exige que no le
     * queden funciones vigentes sin terminar.
     */
    @PatchMapping("/{uuid}")
    public ScreenRoomResponseDTO archive(@PathVariable UUID uuid, @Valid @RequestBody ArchiveRequestDTO request) {
        return screenRoomService.archive(uuid, request.archived());
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        screenRoomService.delete(uuid);
    }
}
