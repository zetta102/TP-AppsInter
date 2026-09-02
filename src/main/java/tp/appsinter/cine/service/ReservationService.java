package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateReservationDTO;
import tp.appsinter.cine.model.dto.request.ReservationItemRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateReservationResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatAvailabilityResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatMapResponseDTO;
import tp.appsinter.cine.model.entities.*;
import tp.appsinter.cine.model.utils.ReservationStatus;
import tp.appsinter.cine.model.utils.SeatCoordinateComparator;
import tp.appsinter.cine.model.utils.SeatType;
import tp.appsinter.cine.repository.InventoryRepository;
import tp.appsinter.cine.repository.ReservationRepository;
import tp.appsinter.cine.repository.SeatRepository;
import tp.appsinter.cine.repository.TicketRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final InventoryRepository inventoryRepository;
    private final MovieScreeningService movieScreeningService;
    private final UserService userService;

    static List<String> distinctSorted(List<Seat> seats, Function<Seat, String> coordinate) {
        return seats.stream()
                .map(coordinate)
                .distinct()
                .sorted(SeatCoordinateComparator.INSTANCE)
                .toList();
    }

    static BigDecimal priceFor(BigDecimal basePrice, SeatType seatType) {
        BigDecimal multiplier = switch (seatType) {
            case NORMAL -> BigDecimal.ONE;
            case PREMIUM -> new BigDecimal("1.50");
        };
        return basePrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public CreateReservationResponseDTO createReservation(String userEmail, CreateReservationDTO request) {
        User user = userService.getByEmail(userEmail);
        MovieScreening screening = movieScreeningService.getScreening(request.movieScreeningId());

        if (screening.getStartsAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessRuleException("Screening %s has already started".formatted(screening.getUuid()));
        }

        List<Seat> seats = seatRepository.findByUuidIn(request.seatIds());
        if (seats.size() != request.seatIds().size()) {
            throw new NotFoundException("One or more requested seats do not exist");
        }

        UUID roomUuid = screening.getScreenRoom().getUuid();
        boolean allInRoom = seats.stream().allMatch(seat -> seat.getScreenRoom().getUuid().equals(roomUuid));
        if (!allInRoom) {
            throw new BusinessRuleException("All seats must belong to room %s".formatted(roomUuid));
        }

        if (ticketRepository.existsByMovieScreeningUuidAndSeatUuidIn(screening.getUuid(), request.seatIds())) {
            throw new ConflictException("One or more seats are already booked for this screening");
        }

        Reservation reservation = Reservation.builder()
                .user(user)
                .movieScreening(screening)
                .status(ReservationStatus.CONFIRMED)
                .createdAt(OffsetDateTime.now())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (Seat seat : seats) {
            BigDecimal price = priceFor(screening.getBasePrice(), seat.getSeatType());
            reservation.addTicket(Ticket.builder()
                    .movieScreening(screening)
                    .seat(seat)
                    .seatType(seat.getSeatType())
                    .price(price)
                    .build());
            total = total.add(price);
        }
        total = total.add(addConcessions(reservation, request.products()));
        reservation.setTotalPrice(total);

        try {
            // Flush inside the transaction so the (screening, seat) unique constraint surfaces here.
            Reservation saved = reservationRepository.saveAndFlush(reservation);
            return CreateReservationResponseDTO.from(saved);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Concurrent booking rejected for screening {}", screening.getUuid(), ex);
            throw new ConflictException("One or more seats were just booked by another customer");
        }
    }

    /**
     * Attaches concession line items and draws down the stock. Name and price are copied onto the
     * line so a later catalogue edit cannot rewrite a past purchase.
     */
    private BigDecimal addConcessions(Reservation reservation, List<ReservationItemRequestDTO> products) {
        if (products.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<UUID> productIds = products.stream().map(ReservationItemRequestDTO::inventoryId).toList();
        Map<UUID, Inventory> byId = inventoryRepository.findByUuidIn(productIds).stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getUuid(), item), Map::putAll);
        if (byId.size() != productIds.size()) {
            throw new NotFoundException("One or more requested products do not exist");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (ReservationItemRequestDTO product : products) {
            Inventory inventory = byId.get(product.inventoryId());
            if (inventory.getQuantity() < product.quantity()) {
                throw new BusinessRuleException(
                        "Only %d unit(s) of '%s' are in stock".formatted(
                                inventory.getQuantity(), inventory.getProductName()));
            }
            inventory.setQuantity(inventory.getQuantity() - product.quantity());

            BigDecimal lineTotal = inventory.getCost()
                    .multiply(BigDecimal.valueOf(product.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            reservation.addItem(ReservationItem.builder()
                    .inventory(inventory)
                    .productName(inventory.getProductName())
                    .unitPrice(inventory.getCost())
                    .quantity(product.quantity())
                    .lineTotal(lineTotal)
                    .build());
            total = total.add(lineTotal);
        }
        return total;
    }

    @Transactional
    public CreateReservationResponseDTO cancelReservation(UUID uuid, String userEmail, boolean isAdmin) {
        Reservation reservation = getReservation(uuid);
        if (!isAdmin && !reservation.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new NotFoundException("Reservation %s not found".formatted(uuid));
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessRuleException("Reservation %s is already cancelled".formatted(uuid));
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setTotalPrice(BigDecimal.ZERO);
        // Removing the tickets releases the seats so they can be booked again.
        reservation.getTickets().clear();
        // Concessions go back on the shelf.
        reservation.getItems().forEach(item -> {
            Inventory inventory = item.getInventory();
            inventory.setQuantity(inventory.getQuantity() + item.getQuantity());
        });
        reservation.getItems().clear();
        return CreateReservationResponseDTO.from(reservationRepository.saveAndFlush(reservation));
    }

    public CreateReservationResponseDTO findById(UUID uuid, String userEmail, boolean isAdmin) {
        Reservation reservation = getReservation(uuid);
        if (!isAdmin && !reservation.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new NotFoundException("Reservation %s not found".formatted(uuid));
        }
        return CreateReservationResponseDTO.from(reservation);
    }

    public PageResponseDTO<CreateReservationResponseDTO> findMine(String userEmail, Pageable pageable) {
        Page<Reservation> page = reservationRepository.findByUserEmailIgnoreCase(userEmail, pageable);
        return PageResponseDTO.from(page, CreateReservationResponseDTO::from);
    }

    public PageResponseDTO<CreateReservationResponseDTO> findAll(ReservationStatus status, Pageable pageable) {
        Page<Reservation> page = status == null
                ? reservationRepository.findAll(pageable)
                : reservationRepository.findByStatus(status, pageable);
        return PageResponseDTO.from(page, CreateReservationResponseDTO::from);
    }

    public SeatMapResponseDTO findSeatMap(UUID screeningUuid) {
        MovieScreening screening = movieScreeningService.getScreening(screeningUuid);
        Set<UUID> booked = new HashSet<>(ticketRepository.findBookedSeatIds(screeningUuid));
        List<Seat> seats = seatRepository.findByScreenRoomUuid(screening.getScreenRoom().getUuid());

        return SeatMapResponseDTO.of(
                screening,
                distinctSorted(seats, seat -> seat.getPosition().row()),
                distinctSorted(seats, seat -> seat.getPosition().column()),
                seats.stream()
                        .map(seat -> SeatAvailabilityResponseDTO.of(seat, !booked.contains(seat.getUuid())))
                        .toList());
    }

    private Reservation getReservation(UUID uuid) {
        return reservationRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Reservation", uuid));
    }
}
