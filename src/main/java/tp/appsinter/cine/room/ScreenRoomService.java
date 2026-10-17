package tp.appsinter.cine.room;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.room.dto.CreateScreenRoomRequestDTO;
import tp.appsinter.cine.room.dto.ScreenRoomResponseDTO;
import tp.appsinter.cine.screening.MovieScreeningRepository;
import tp.appsinter.cine.shared.dto.PageResponseDTO;
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
    public ScreenRoomResponseDTO create(CreateScreenRoomRequestDTO request) {
        if (screenRoomRepository.existsByRoomNameIgnoreCase(request.roomName())) {
            throw ConflictException.nameTaken(
                    "Screen room '%s' already exists".formatted(request.roomName()),
                    "roomName", request.roomName());
        }
        ScreenRoom room = ScreenRoom.builder()
                .roomName(request.roomName())
                .screenRoomType(request.screenRoomType())
                .build();
        return ScreenRoomResponseDTO.from(screenRoomRepository.save(room));
    }

    /**
     * @param includeArchived si es falso, las salas archivadas no aparecen
     */
    public PageResponseDTO<ScreenRoomResponseDTO> findAll(boolean includeArchived, Pageable pageable) {
        Page<ScreenRoom> rooms = includeArchived
                ? screenRoomRepository.findAll(pageable)
                : screenRoomRepository.findByArchivedAtIsNull(pageable);
        return PageResponseDTO.from(rooms, ScreenRoomResponseDTO::from);
    }

    public ScreenRoomResponseDTO findById(UUID uuid) {
        return ScreenRoomResponseDTO.from(getScreenRoom(uuid));
    }

    @Transactional
    public ScreenRoomResponseDTO update(UUID uuid, CreateScreenRoomRequestDTO request) {
        ScreenRoom room = getScreenRoom(uuid);
        if (screenRoomRepository.existsByRoomNameIgnoreCaseAndUuidNot(request.roomName(), uuid)) {
            throw ConflictException.nameTaken(
                    "Screen room '%s' already exists".formatted(request.roomName()),
                    "roomName", request.roomName());
        }
        room.setRoomName(request.roomName());
        room.setScreenRoomType(request.screenRoomType());
        return ScreenRoomResponseDTO.from(room);
    }

    /**
     * Archiva o restaura una sala. Archivarla exige que no le queden funciones vigentes sin
     * terminar ({@code 409 RESOURCE_IN_USE}).
     */
    @Transactional
    public ScreenRoomResponseDTO archive(UUID uuid, boolean archived) {
        ScreenRoom room = getScreenRoom(uuid);
        if (archived == room.isArchived()) {
            return ScreenRoomResponseDTO.from(room);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (archived && movieScreeningRepository.existsByScreenRoomUuidAndArchivedAtIsNullAndEndsAtAfter(uuid, now)) {
            throw ConflictException.resourceInUse(
                    "Screen room %s still has upcoming screenings; archive them first".formatted(uuid),
                    "Screen room", "Movie screening");
        }
        room.setArchivedAt(archived ? now : null);
        return ScreenRoomResponseDTO.from(room);
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
