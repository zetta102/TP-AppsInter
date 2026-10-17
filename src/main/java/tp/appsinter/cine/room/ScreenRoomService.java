package tp.appsinter.cine.room;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.room.dto.request.RoomRequestDTO;
import tp.appsinter.cine.room.dto.response.RoomResponseDTO;
import tp.appsinter.cine.screening.MovieScreeningRepository;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.NotFoundException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenRoomService {

    private final ScreenRoomRepository screenRoomRepository;
    private final MovieScreeningRepository movieScreeningRepository;

    @Transactional
    public RoomResponseDTO create(RoomRequestDTO request) {
        if (screenRoomRepository.existsByRoomNameIgnoreCase(request.roomName())) {
            throw ConflictException.nameTaken(
                    "Screen room '%s' already exists".formatted(request.roomName()),
                    "roomName", request.roomName());
        }
        ScreenRoom room = ScreenRoom.builder()
                .roomName(request.roomName())
                .screenRoomType(request.screenRoomType())
                .build();
        return RoomResponseDTO.from(screenRoomRepository.save(room));
    }

    /**
     * @param includeArchived si es falso, las salas archivadas no aparecen
     */
    public PageResponseDTO<RoomResponseDTO> findAll(boolean includeArchived, Pageable pageable) {
        Page<ScreenRoom> rooms = includeArchived
                ? screenRoomRepository.findAll(pageable)
                : screenRoomRepository.findByArchivedAtIsNull(pageable);
        return PageResponseDTO.from(rooms, RoomResponseDTO::from);
    }

    public RoomResponseDTO findById(UUID uuid) {
        return RoomResponseDTO.from(getScreenRoom(uuid));
    }

    @Transactional
    public RoomResponseDTO update(UUID uuid, RoomRequestDTO request) {
        ScreenRoom room = getScreenRoom(uuid);
        if (screenRoomRepository.existsByRoomNameIgnoreCaseAndUuidNot(request.roomName(), uuid)) {
            throw ConflictException.nameTaken(
                    "Screen room '%s' already exists".formatted(request.roomName()),
                    "roomName", request.roomName());
        }
        room.setRoomName(request.roomName());
        room.setScreenRoomType(request.screenRoomType());
        return RoomResponseDTO.from(room);
    }

    /**
     * Archiva o restaura una sala. Archivarla exige que no le queden funciones vigentes sin
     * terminar ({@code 409 RESOURCE_IN_USE}).
     */
    @Transactional
    public RoomResponseDTO archive(UUID uuid, boolean archived) {
        ScreenRoom room = getScreenRoom(uuid);
        if (archived == room.isArchived()) {
            return RoomResponseDTO.from(room);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (archived && movieScreeningRepository.existsByScreenRoomUuidAndArchivedAtIsNullAndEndsAtAfter(uuid, now)) {
            throw ConflictException.resourceInUse(
                    "Screen room %s still has upcoming screenings; archive them first".formatted(uuid),
                    "Screen room", "Movie screening");
        }
        room.setArchivedAt(archived ? now : null);
        return RoomResponseDTO.from(room);
    }

    @Transactional
    public void delete(UUID uuid) {
        screenRoomRepository.delete(getScreenRoom(uuid));
    }

    public ScreenRoom getScreenRoom(UUID uuid) {
        return screenRoomRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Screen room", uuid));
    }

    /**
     * Como {@link #getScreenRoom}, pero rechaza una sala archivada ({@code 422 RESOURCE_ARCHIVED}).
     */
    public ScreenRoom getActiveScreenRoom(UUID uuid) {
        ScreenRoom room = getScreenRoom(uuid);
        if (room.isArchived()) {
            throw BusinessRuleException.resourceArchived("Screen room", uuid);
        }
        return room;
    }
}
