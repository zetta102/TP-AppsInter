package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateScreenRoomDTO;
import tp.appsinter.cine.model.dto.response.CreateScreenRoomResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.entities.ScreenRoom;
import tp.appsinter.cine.repository.ScreenRoomRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenRoomService {

    private final ScreenRoomRepository screenRoomRepository;

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

    public PageResponseDTO<CreateScreenRoomResponseDTO> findAll(Pageable pageable) {
        return PageResponseDTO.from(screenRoomRepository.findAll(pageable), CreateScreenRoomResponseDTO::from);
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

    @Transactional
    public void delete(UUID uuid) {
        screenRoomRepository.delete(getScreenRoom(uuid));
    }

    ScreenRoom getScreenRoom(UUID uuid) {
        return screenRoomRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Screen room", uuid));
    }
}
