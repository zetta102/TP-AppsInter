package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateSeatRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateSeatResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatLayoutResponseDTO;
import tp.appsinter.cine.model.entities.ScreenRoom;
import tp.appsinter.cine.model.entities.Seat;
import tp.appsinter.cine.model.utils.Position;
import tp.appsinter.cine.repository.SeatRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatService {

    private final SeatRepository seatRepository;
    private final ScreenRoomService screenRoomService;

    @Transactional
    public CreateSeatResponseDTO create(CreateSeatRequestDTO request) {
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());
        Position position = new Position(request.row(), request.column());
        if (seatRepository.existsByScreenRoomUuidAndPositionRowAndPositionColumn(
                room.getUuid(), position.row(), position.column())) {
            throw new ConflictException(
                    "Seat %s%s already exists in room %s".formatted(position.row(), position.column(), room.getRoomName()));
        }
        Seat seat = Seat.builder()
                .position(position)
                .seatType(request.seatType())
                .screenRoom(room)
                .build();
        return CreateSeatResponseDTO.from(seatRepository.save(seat));
    }

    public List<CreateSeatResponseDTO> findByScreenRoom(UUID screenRoomId) {
        screenRoomService.getScreenRoom(screenRoomId);
        return seatRepository.findByScreenRoomUuid(screenRoomId).stream()
                .map(CreateSeatResponseDTO::from)
                .toList();
    }

    /**
     * Grid metadata so a client can render the room without inferring its dimensions.
     */
    public SeatLayoutResponseDTO findLayout(UUID screenRoomId) {
        ScreenRoom room = screenRoomService.getScreenRoom(screenRoomId);
        List<Seat> seats = seatRepository.findByScreenRoomUuid(screenRoomId);
        return SeatLayoutResponseDTO.of(
                room,
                ReservationService.distinctSorted(seats, seat -> seat.getPosition().row()),
                ReservationService.distinctSorted(seats, seat -> seat.getPosition().column()),
                seats.stream().map(CreateSeatResponseDTO::from).toList());
    }

    public CreateSeatResponseDTO findById(UUID uuid) {
        return CreateSeatResponseDTO.from(getSeat(uuid));
    }

    @Transactional
    public CreateSeatResponseDTO update(UUID uuid, CreateSeatRequestDTO request) {
        Seat seat = getSeat(uuid);
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());
        Position position = new Position(request.row(), request.column());
        if (seatRepository.existsByScreenRoomUuidAndPositionRowAndPositionColumnAndUuidNot(
                room.getUuid(), position.row(), position.column(), uuid)) {
            throw new ConflictException(
                    "Seat %s%s already exists in room %s".formatted(position.row(), position.column(), room.getRoomName()));
        }
        seat.setPosition(position);
        seat.setSeatType(request.seatType());
        seat.setScreenRoom(room);
        return CreateSeatResponseDTO.from(seat);
    }

    @Transactional
    public void delete(UUID uuid) {
        seatRepository.delete(getSeat(uuid));
    }

    Seat getSeat(UUID uuid) {
        return seatRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Seat", uuid));
    }
}
