package tp.appsinter.cine.room;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.room.dto.request.RoomRequestDTO;
import tp.appsinter.cine.room.dto.response.RoomResponseDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.shared.persistence.IdCount;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenRoomService {

    private final ScreenRoomRepository screenRoomRepository;
    private final SeatRepository seatRepository;
    private final RoomScreenings roomScreenings;
    private final Clock clock;

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
        return RoomResponseDTO.from(screenRoomRepository.save(room), 0);
    }

    /**
     * @param includeArchived si es falso, las salas archivadas no aparecen
     */
    public PageResponseDTO<RoomResponseDTO> findAll(boolean includeArchived, Pageable pageable) {
        Page<ScreenRoom> rooms = includeArchived
                ? screenRoomRepository.findAll(pageable)
                : screenRoomRepository.findByArchivedAtIsNull(pageable);
        List<UUID> roomIds = rooms.getContent().stream().map(ScreenRoom::getUuid).toList();
        Map<UUID, Integer> seatCounts = roomIds.isEmpty()
                ? Map.of()
                : IdCount.toMap(seatRepository.countByScreenRoom(roomIds));
        return PageResponseDTO.from(rooms,
                room -> RoomResponseDTO.from(room, seatCounts.getOrDefault(room.getUuid(), 0)));
    }

    public RoomResponseDTO findById(UUID uuid) {
        return describe(getScreenRoom(uuid));
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
        return describe(room);
    }

    /**
     * Archiva o restaura una sala. Archivarla exige que no le queden funciones vigentes sin
     * terminar ({@code 409 RESOURCE_IN_USE}).
     */
    @Transactional
    public RoomResponseDTO archive(UUID uuid, boolean archived) {
        ScreenRoom room = getScreenRoom(uuid);
        if (archived == room.isArchived()) {
            return describe(room);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (archived && roomScreenings.hasUpcoming(uuid, now)) {
            throw ConflictException.resourceInUse(
                    "Screen room %s still has upcoming screenings; archive them first".formatted(uuid),
                    "Screen room", "Movie screening");
        }
        room.setArchivedAt(archived ? now : null);
        return describe(room);
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

    private RoomResponseDTO describe(ScreenRoom room) {
        return RoomResponseDTO.from(room,
                IdCount.toMap(seatRepository.countByScreenRoom(List.of(room.getUuid())))
                        .getOrDefault(room.getUuid(), 0));
    }
}
