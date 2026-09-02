package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.request.CreateScreenRoomDTO;
import tp.appsinter.cine.model.dto.response.CreateScreenRoomResponseDTO;
import tp.appsinter.cine.model.dto.response.CreateSeatResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatLayoutResponseDTO;
import tp.appsinter.cine.service.ScreenRoomService;
import tp.appsinter.cine.service.SeatService;

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
    public ResponseEntity<CreateScreenRoomResponseDTO> create(@Valid @RequestBody CreateScreenRoomDTO request) {
        CreateScreenRoomResponseDTO created = screenRoomService.create(request);
        return ResponseEntity.created(URI.create("/api/rooms/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<CreateScreenRoomResponseDTO> findAll(
            @PageableDefault(size = 20, sort = "roomName") Pageable pageable) {
        return screenRoomService.findAll(pageable);
    }

    @GetMapping("/{uuid}")
    public CreateScreenRoomResponseDTO findById(@PathVariable UUID uuid) {
        return screenRoomService.findById(uuid);
    }

    @GetMapping("/{uuid}/seats")
    public List<CreateSeatResponseDTO> findSeats(@PathVariable UUID uuid) {
        return seatService.findByScreenRoom(uuid);
    }

    @GetMapping("/{uuid}/layout")
    public SeatLayoutResponseDTO findLayout(@PathVariable UUID uuid) {
        return seatService.findLayout(uuid);
    }

    @PutMapping("/{uuid}")
    public CreateScreenRoomResponseDTO update(@PathVariable UUID uuid,
                                              @Valid @RequestBody CreateScreenRoomDTO request) {
        return screenRoomService.update(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        screenRoomService.delete(uuid);
    }
}
