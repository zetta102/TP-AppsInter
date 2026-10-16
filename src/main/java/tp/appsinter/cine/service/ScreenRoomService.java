package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateScreenRoomDTO;
import tp.appsinter.cine.model.dto.response.CreateScreenRoomResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.entities.ScreenRoom;
import tp.appsinter.cine.repository.MovieScreeningRepository;
import tp.appsinter.cine.repository.ScreenRoomRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenRoomService {

    private final ScreenRoomRepository screenRoomRepository;
    private final MovieScreeningRepository movieScreeningRepository;

    @Transactional
    public CreateScreenRoomResponseDTO create(CreateScreenRoomDTO request) {
        if (screenRoomRepository.existsByRoomNameIgnoreCase(request.roomName())) {
            throw ConflictException.nameTaken(
                    "Screen room '%s' already exists".formatted(request.roomName()),
                    "roomName", request.roomName());
        }
        ScreenRoom room = ScreenRoom.builder()
                .roomName(request.roomName())
                .screenRoomType(request.screenRoomType())
                .build();
        return CreateScreenRoomResponseDTO.from(screenRoomRepository.save(room));
    }

    /**
     * @param includeArchived si es falso, las salas archivadas no aparecen
     */
    public PageResponseDTO<CreateScreenRoomResponseDTO> findAll(boolean includeArchived, Pageable pageable) {
        Page<ScreenRoom> rooms = includeArchived
                ? screenRoomRepository.findAll(pageable)
                : screenRoomRepository.findByArchivedAtIsNull(pageable);
        return PageResponseDTO.from(rooms, CreateScreenRoomResponseDTO::from);
    }

    public CreateScreenRoomResponseDTO findById(UUID uuid) {
        return CreateScreenRoomResponseDTO.from(getScreenRoom(uuid));
    }

    @Transactional
    public CreateScreenRoomResponseDTO update(UUID uuid, CreateScreenRoomDTO request) {
        ScreenRoom room = getScreenRoom(uuid);
        if (screenRoomRepository.existsByRoomNameIgnoreCaseAndUuidNot(request.roomName(), uuid)) {
            throw ConflictException.nameTaken(
                    "Screen room '%s' already exists".formatted(request.roomName()),
                    "roomName", request.roomName());
        }
        room.setRoomName(request.roomName());
        room.setScreenRoomType(request.screenRoomType());
        return CreateScreenRoomResponseDTO.from(room);
    }

    /**
     * Archiva o restaura una sala. Archivarla exige que no le queden funciones vigentes sin
     * terminar ({@code 409 RESOURCE_IN_USE}).
     */
    @Transactional
    public CreateScreenRoomResponseDTO archive(UUID uuid, boolean archived) {
        ScreenRoom room = getScreenRoom(uuid);
        if (archived == room.isArchived()) {
            return CreateScreenRoomResponseDTO.from(room);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (archived && movieScreeningRepository.existsByScreenRoomUuidAndArchivedAtIsNullAndEndsAtAfter(uuid, now)) {
            throw ConflictException.resourceInUse(
                    "Screen room %s still has upcoming screenings; archive them first".formatted(uuid),
                    "Screen room", "Movie screening");
        }
        room.setArchivedAt(archived ? now : null);
        return CreateScreenRoomResponseDTO.from(room);
    }

    @Transactional
    public void delete(UUID uuid) {
        screenRoomRepository.delete(getScreenRoom(uuid));
    }

    ScreenRoom getScreenRoom(UUID uuid) {
        return screenRoomRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Screen room", uuid));
    }

    /**
     * Como {@link #getScreenRoom}, pero rechaza una sala archivada ({@code 422 RESOURCE_ARCHIVED}).
     */
    ScreenRoom getActiveScreenRoom(UUID uuid) {
        ScreenRoom room = getScreenRoom(uuid);
        if (room.isArchived()) {
            throw BusinessRuleException.resourceArchived("Screen room", uuid);
        }
        return room;
    }
}
