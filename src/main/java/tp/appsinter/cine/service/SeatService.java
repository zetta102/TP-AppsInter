package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.BulkSeatRequestDTO;
import tp.appsinter.cine.model.dto.request.CreateSeatRequestDTO;
import tp.appsinter.cine.model.dto.request.GenerateSeatsRequestDTO;
import tp.appsinter.cine.model.dto.request.SeatConflictPolicy;
import tp.appsinter.cine.model.dto.response.CreateSeatResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatLayoutResponseDTO;
import tp.appsinter.cine.model.entities.ScreenRoom;
import tp.appsinter.cine.model.entities.Seat;
import tp.appsinter.cine.model.utils.Position;
import tp.appsinter.cine.model.utils.SeatType;
import tp.appsinter.cine.repository.SeatRepository;
import tp.appsinter.cine.repository.TicketRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatService {

    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final ScreenRoomService screenRoomService;

    @Transactional
    public CreateSeatResponseDTO create(CreateSeatRequestDTO request) {
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());
        Position position = new Position(request.row(), request.column());
        if (seatRepository.existsByScreenRoomUuidAndPositionRowAndPositionColumn(
                room.getUuid(), position.row(), position.column())) {
            throw ConflictException.nameTaken(
                    "Seat %s%s already exists in room %s".formatted(position.row(), position.column(), room.getRoomName()),
                    "position", position.row() + position.column());
        }
        Seat seat = Seat.builder()
                .position(position)
                .seatType(request.seatType())
                .screenRoom(room)
                .build();
        return CreateSeatResponseDTO.from(seatRepository.save(seat));
    }

    /**
     * Alta de un lote de butacas en una sola transacción.
     *
     * <p>Todo el lote comparte suerte: si la política es {@code FAIL} y una sola posición choca, no
     * se crea ninguna. Es lo que hace que la operación sea reintentable sin dejar la sala a medio
     * construir.
     */
    @Transactional
    public SeatLayoutResponseDTO createBulk(UUID screenRoomId, BulkSeatRequestDTO request) {
        ScreenRoom room = screenRoomService.getScreenRoom(screenRoomId);
        return apply(room, request.seats(), Objects.requireNonNull(request.onConflict()));
    }

    /**
     * Genera una grilla rectangular de butacas.
     *
     * <p>Cuando no se indica {@code startRow} y la sala ya tiene filas, se continúa después de la
     * última: así se puede agrandar una sala llamando dos veces sin calcular nombres a mano.
     */
    @Transactional
    public SeatLayoutResponseDTO generate(UUID screenRoomId, GenerateSeatsRequestDTO request) {
        ScreenRoom room = screenRoomService.getScreenRoom(screenRoomId);
        int firstRow = request.startRow() != null
                ? rowIndex(request.startRow())
                : nextFreeRowIndex(screenRoomId);

        Set<String> premium = Objects.requireNonNull(request.premiumRows()).stream()
                .map(row -> row.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> skipped = Objects.requireNonNull(request.skip()).stream()
                .map(position -> position.toUpperCase(Locale.ROOT).replace(" ", ""))
                .collect(Collectors.toSet());

        List<BulkSeatRequestDTO.SeatDefinition> seats = new ArrayList<>();
        for (int r = 0; r < request.rows(); r++) {
            String row = rowName(firstRow + r);
            SeatType type = premium.contains(row) ? SeatType.PREMIUM : SeatType.NORMAL;
            for (int c = 1; c <= request.columns(); c++) {
                String column = String.valueOf(c);
                if (skipped.contains(row + column)) {
                    continue;
                }
                seats.add(new BulkSeatRequestDTO.SeatDefinition(row, column, type));
            }
        }
        if (seats.isEmpty()) {
            throw BusinessRuleException.emptyLayout();
        }
        return apply(room, seats, Objects.requireNonNull(request.onConflict()));
    }

    /**
     * Núcleo compartido por el alta masiva y el generador.
     *
     * <p>Lee de una vez las butacas que ya tiene la sala para no consultar la base una vez por
     * posición, y detecta también los duplicados dentro del propio lote, que de otro modo sólo
     * saltarían como violación de índice sin decir cuál era la fila culpable.
     */
    private SeatLayoutResponseDTO apply(ScreenRoom room,
                                        List<BulkSeatRequestDTO.SeatDefinition> definitions,
                                        SeatConflictPolicy onConflict) {
        Map<String, Seat> existing = seatRepository.findByScreenRoomUuid(room.getUuid()).stream()
                .collect(Collectors.toMap(seat -> key(seat.getPosition().row(), seat.getPosition().column()),
                        seat -> seat));

        List<String> conflicts = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<Seat> toCreate = new ArrayList<>();

        for (BulkSeatRequestDTO.SeatDefinition definition : definitions) {
            String row = definition.row().trim().toUpperCase(Locale.ROOT);
            String column = definition.column().trim();
            String key = key(row, column);
            if (!seen.add(key)) {
                duplicates.add(key);
                continue;
            }
            Seat current = existing.get(key);
            if (current == null) {
                toCreate.add(Seat.builder()
                        .position(new Position(row, column))
                        .seatType(definition.seatType())
                        .screenRoom(room)
                        .build());
                continue;
            }
            switch (onConflict) {
                case FAIL -> conflicts.add(key);
                case SKIP -> { }
                case REPLACE -> current.setSeatType(definition.seatType());
            }
        }

        if (!duplicates.isEmpty()) {
            throw ConflictException.seatPositionsTaken(
                    "The request repeats the same seat position more than once", duplicates);
        }
        if (!conflicts.isEmpty()) {
            throw ConflictException.seatPositionsTaken(
                    "Some seats already exist in room %s".formatted(room.getRoomName()), conflicts);
        }
        seatRepository.saveAll(toCreate);
        return findLayout(room.getUuid());
    }

    private static String key(String row, String column) {
        return row + column;
    }

    /**
     * Traduce un nombre de fila a su posición: {@code A} es 0, {@code Z} es 25, {@code AA} es 26.
     */
    private static int rowIndex(String row) {
        int index = 0;
        for (char c : row.toUpperCase(Locale.ROOT).toCharArray()) {
            index = index * 26 + (c - 'A' + 1);
        }
        return index - 1;
    }

    private static String rowName(int index) {
        StringBuilder name = new StringBuilder();
        for (int i = index; i >= 0; i = i / 26 - 1) {
            name.insert(0, (char) ('A' + i % 26));
        }
        return name.toString();
    }

    private int nextFreeRowIndex(UUID screenRoomId) {
        return seatRepository.findByScreenRoomUuid(screenRoomId).stream()
                .map(seat -> rowIndex(seat.getPosition().row()))
                .max(Integer::compareTo)
                .map(last -> last + 1)
                .orElse(0);
    }

    public List<CreateSeatResponseDTO> findByScreenRoom(UUID screenRoomId) {
        screenRoomService.getScreenRoom(screenRoomId);
        return seatRepository.findByScreenRoomUuid(screenRoomId).stream()
                .map(CreateSeatResponseDTO::from)
                .toList();
    }

    /**
     * Metadatos de la grilla para que un cliente pueda dibujar la sala sin deducir sus dimensiones.
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

    /**
     * Corrige una butaca.
     *
     * <p>Una butaca que ya figura en alguna entrada no puede cambiar de sala ni de posición: las
     * entradas muestran la posición de la butaca, así que moverla reescribiría el historial de lo
     * vendido. El tipo sí puede cambiar, porque cada entrada conserva el tipo y el precio con que se
     * vendió.
     */
    @Transactional
    public CreateSeatResponseDTO update(UUID uuid, CreateSeatRequestDTO request) {
        Seat seat = getSeat(uuid);
        ScreenRoom room = screenRoomService.getScreenRoom(request.screenRoomId());
        Position position = new Position(request.row(), request.column());
        boolean moved = !seat.getScreenRoom().getUuid().equals(room.getUuid())
                || !seat.getPosition().equals(position);
        if (moved && ticketRepository.existsBySeatUuid(uuid)) {
            throw ConflictException.resourceInUse(
                    "Seat %s already appears on tickets; only its type can change".formatted(uuid),
                    "Seat", "Ticket");
        }
        if (seatRepository.existsByScreenRoomUuidAndPositionRowAndPositionColumnAndUuidNot(
                room.getUuid(), position.row(), position.column(), uuid)) {
            throw ConflictException.nameTaken(
                    "Seat %s%s already exists in room %s".formatted(position.row(), position.column(), room.getRoomName()),
                    "position", position.row() + position.column());
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
