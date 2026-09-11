package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateReservationDTO;
import tp.appsinter.cine.model.dto.request.ReservationFilterDTO;
import tp.appsinter.cine.model.dto.request.ReservationItemRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateReservationResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.dto.response.ReservationQuoteResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatAvailabilityResponseDTO;
import tp.appsinter.cine.model.dto.response.SeatMapResponseDTO;
import tp.appsinter.cine.model.entities.*;
import tp.appsinter.cine.model.utils.BusinessConstants;
import tp.appsinter.cine.model.utils.ReservationStatus;
import tp.appsinter.cine.model.utils.SeatCoordinateComparator;
import tp.appsinter.cine.model.utils.SeatType;
import tp.appsinter.cine.repository.InventoryRepository;
import tp.appsinter.cine.repository.ReservationRepository;
import tp.appsinter.cine.repository.ReservationSpecifications;
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
    private final SeatHoldService seatHoldService;

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
            case PREMIUM -> BusinessConstants.PREMIUM_SEAT_MULTIPLIER;
        };
        return basePrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public CreateReservationResponseDTO createReservation(String userEmail, CreateReservationDTO request) {
        User user = userService.getByEmail(userEmail);
        if (request.holdId() != null) {
            // Se consume antes de validar el resto para que el bloqueo deje de contar como ajeno y
            // no se rechace la compra por chocar con la reserva temporal del propio comprador.
            seatHoldService.consume(request.holdId(), userEmail, request.movieScreeningId(), request.seatIds());
        }
        PricedBasket basket = price(request);
        seatHoldService.requireNotHeldByOthers(request.movieScreeningId(), request.seatIds(), user.getUuid());
        MovieScreening screening = basket.screening();

        Reservation reservation = Reservation.builder()
                .user(user)
                .movieScreening(screening)
                .status(ReservationStatus.CONFIRMED)
                .createdAt(OffsetDateTime.now())
                .build();

        for (PricedSeat seat : basket.seats()) {
            reservation.addTicket(Ticket.builder()
                    .movieScreening(screening)
                    .seat(seat.seat())
                    .seatType(seat.seat().getSeatType())
                    .price(seat.price())
                    .build());
        }
        for (PricedProduct product : basket.products()) {
            Inventory inventory = product.inventory();
            // El stock se descuenta sólo acá: presupuestar no debe mover la góndola.
            inventory.setQuantity(inventory.getQuantity() - product.quantity());
            reservation.addItem(ReservationItem.builder()
                    .inventory(inventory)
                    .productName(inventory.getProductName())
                    .unitPrice(inventory.getCost())
                    .quantity(product.quantity())
                    .lineTotal(product.lineTotal())
                    .build());
        }
        reservation.setTotalPrice(basket.totalPrice());

        try {
            // Se guarda dentro de la transacción para que la restricción única (función, butaca)
            // aflore aquí y no al confirmar.
            Reservation saved = reservationRepository.saveAndFlush(reservation);
            return CreateReservationResponseDTO.from(saved);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Concurrent booking rejected for screening {}", screening.getUuid(), ex);
            throw ConflictException.seatsUnavailable(
                    "One or more seats were just booked by another customer", request.seatIds());
        }
    }

    /**
     * Calcula lo que costaría una reserva sin crearla.
     *
     * <p>Recorre exactamente la misma cadena de validación que el alta, así que un presupuesto que
     * responde 200 es también una reserva que se puede confirmar —salvo que alguien se adelante a
     * tomar las butacas— y los mismos 404, 409 y 422 aparecen acá primero.
     */
    public ReservationQuoteResponseDTO quote(CreateReservationDTO request) {
        PricedBasket basket = price(request);
        return new ReservationQuoteResponseDTO(
                basket.seats().stream()
                        .map(seat -> new ReservationQuoteResponseDTO.QuotedTicket(
                                seat.seat().getUuid(),
                                seat.seat().getPosition().row(),
                                seat.seat().getPosition().column(),
                                seat.seat().getSeatType(),
                                seat.price()))
                        .toList(),
                basket.products().stream()
                        .map(product -> new ReservationQuoteResponseDTO.QuotedItem(
                                product.inventory().getUuid(),
                                product.inventory().getProductName(),
                                product.inventory().getCost(),
                                product.quantity(),
                                product.lineTotal()))
                        .toList(),
                basket.ticketsTotal(),
                basket.itemsTotal(),
                basket.totalPrice());
    }

    /**
     * Valida un pedido y le pone precio, sin tocar nada.
     *
     * <p>Es el único lugar donde vive la cadena de reglas de la compra, para que presupuestar y
     * confirmar no puedan discrepar. Las comprobaciones van de la más barata a la más cara y de la
     * más general a la más específica: primero que la función exista y no haya empezado, después que
     * las butacas existan y pertenezcan a la sala, después que estén libres, y recién al final la
     * confitería.
     */
    private PricedBasket price(CreateReservationDTO request) {
        MovieScreening screening = movieScreeningService.getScreening(request.movieScreeningId());

        if (screening.getStartsAt().isBefore(OffsetDateTime.now())) {
            throw BusinessRuleException.screeningStarted(screening.getUuid(), screening.getStartsAt());
        }

        List<Seat> seats = seatRepository.findByUuidIn(request.seatIds());
        if (seats.size() != request.seatIds().size()) {
            throw new NotFoundException("One or more requested seats do not exist", "Seat");
        }

        UUID roomUuid = screening.getScreenRoom().getUuid();
        boolean allInRoom = seats.stream().allMatch(seat -> seat.getScreenRoom().getUuid().equals(roomUuid));
        if (!allInRoom) {
            throw BusinessRuleException.seatsWrongRoom(roomUuid, request.seatIds());
        }

        List<UUID> taken = ticketRepository.findBookedSeatIdsAmong(screening.getUuid(), request.seatIds());
        if (!taken.isEmpty()) {
            throw ConflictException.seatsUnavailable(
                    "One or more seats are already booked for this screening", taken);
        }

        List<PricedSeat> pricedSeats = seats.stream()
                .map(seat -> new PricedSeat(seat, priceFor(screening.getBasePrice(), seat.getSeatType())))
                .toList();
        return new PricedBasket(screening, pricedSeats, priceProducts(request.products()));
    }

    /**
     * Comprueba existencia y stock de la confitería y calcula cada línea, sin descontar nada.
     */
    private List<PricedProduct> priceProducts(List<ReservationItemRequestDTO> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<UUID> productIds = products.stream().map(ReservationItemRequestDTO::inventoryId).toList();
        Map<UUID, Inventory> byId = inventoryRepository.findByUuidIn(productIds).stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getUuid(), item), Map::putAll);
        if (byId.size() != productIds.size()) {
            throw new NotFoundException("One or more requested products do not exist", "Inventory product");
        }

        List<PricedProduct> priced = new ArrayList<>(products.size());
        for (ReservationItemRequestDTO product : products) {
            Inventory inventory = byId.get(product.inventoryId());
            if (inventory.getQuantity() < product.quantity()) {
                throw BusinessRuleException.insufficientStock(
                        "Only %d unit(s) of '%s' are in stock".formatted(
                                inventory.getQuantity(), inventory.getProductName()),
                        inventory.getUuid(), product.quantity(), inventory.getQuantity());
            }
            BigDecimal lineTotal = inventory.getCost()
                    .multiply(BigDecimal.valueOf(product.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            priced.add(new PricedProduct(inventory, product.quantity(), lineTotal));
        }
        return priced;
    }

    /**
     * Una butaca con el precio que le corresponde en esta función.
     */
    private record PricedSeat(Seat seat, BigDecimal price) {
    }

    /**
     * Una línea de confitería ya valorizada. Guarda la entidad porque quien confirma necesita
     * descontarle stock.
     */
    private record PricedProduct(Inventory inventory, Integer quantity, BigDecimal lineTotal) {
    }

    /**
     * Un pedido validado y con precio, listo para confirmarse o para devolverse como presupuesto.
     */
    private record PricedBasket(MovieScreening screening,
                                List<PricedSeat> seats,
                                List<PricedProduct> products) {

        BigDecimal ticketsTotal() {
            return sum(seats.stream().map(PricedSeat::price));
        }

        BigDecimal itemsTotal() {
            return sum(products.stream().map(PricedProduct::lineTotal));
        }

        BigDecimal totalPrice() {
            return ticketsTotal().add(itemsTotal());
        }

        private static BigDecimal sum(java.util.stream.Stream<BigDecimal> amounts) {
            return amounts.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Cancela una reserva conservando su historia.
     *
     * <p>Las entradas y las líneas de confitería no se borran: se marcan. Así el cliente puede ver
     * qué canceló, la administración puede auditarlo y los informes de ingresos siguen teniendo con
     * qué trabajar. Lo único que se pierde es la ocupación de las butacas, que vuelven a estar a la
     * venta porque el índice único parcial ignora las entradas liberadas.
     */
    @Transactional
    public CreateReservationResponseDTO cancelReservation(UUID uuid, String userEmail, boolean isAdmin) {
        Reservation reservation = getReservation(uuid);
        if (!isAdmin && !reservation.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw NotFoundException.of("Reservation", uuid);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw BusinessRuleException.alreadyCancelled(uuid);
        }

        OffsetDateTime cancelledAt = OffsetDateTime.now();
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(cancelledAt);
        // totalPrice pasa a significar "lo que se debe"; lo que se dejó de cobrar queda aparte.
        reservation.setOriginalTotalPrice(reservation.getTotalPrice());
        reservation.setTotalPrice(BigDecimal.ZERO);
        reservation.getTickets().forEach(ticket -> ticket.setCancelledAt(cancelledAt));
        // La confitería vuelve a la góndola.
        reservation.getItems().forEach(item -> {
            Inventory inventory = item.getInventory();
            inventory.setQuantity(inventory.getQuantity() + item.getQuantity());
        });
        return CreateReservationResponseDTO.from(reservationRepository.saveAndFlush(reservation));
    }

    public CreateReservationResponseDTO findById(UUID uuid, String userEmail, boolean isAdmin) {
        Reservation reservation = getReservation(uuid);
        if (!isAdmin && !reservation.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw NotFoundException.of("Reservation", uuid);
        }
        return CreateReservationResponseDTO.from(reservation);
    }

    /**
     * Historial del cliente autenticado, restringido a sus propias reservas.
     *
     * <p>El filtro de titular se aplica acá y no en el controlador: así ningún parámetro de consulta
     * puede ampliarlo a las reservas de otra persona.
     */
    public PageResponseDTO<CreateReservationResponseDTO> findMine(String userEmail,
                                                                 ReservationFilterDTO filter,
                                                                 Pageable pageable) {
        Specification<Reservation> spec = ReservationSpecifications.withAssociations()
                .and(ReservationSpecifications.ownedBy(userEmail))
                .and(ReservationSpecifications.hasStatus(filter.status()))
                .and(ReservationSpecifications.hasMovie(filter.movieId()))
                .and(ReservationSpecifications.hasScreening(filter.screeningId()))
                .and(ReservationSpecifications.upcoming(filter.upcomingOnly()))
                .and(ReservationSpecifications.screeningBetween(filter.from(), filter.to()));
        return PageResponseDTO.from(reservationRepository.findAll(spec, pageable),
                CreateReservationResponseDTO::from);
    }

    /**
     * Vista de administración sobre todos los clientes.
     *
     * <p>Acá {@code from}/{@code to} acotan la fecha de compra, no la de la función: un operador
     * suele reconstruir lo vendido en una jornada.
     */
    public PageResponseDTO<CreateReservationResponseDTO> findAll(ReservationFilterDTO filter, Pageable pageable) {
        Specification<Reservation> spec = ReservationSpecifications.withAssociations()
                .and(ReservationSpecifications.hasStatus(filter.status()))
                .and(ReservationSpecifications.hasUser(filter.userId()))
                .and(ReservationSpecifications.emailContains(filter.email()))
                .and(ReservationSpecifications.hasScreening(filter.screeningId()))
                .and(ReservationSpecifications.hasMovie(filter.movieId()))
                .and(ReservationSpecifications.upcoming(filter.upcomingOnly()))
                .and(ReservationSpecifications.createdBetween(filter.from(), filter.to()));
        return PageResponseDTO.from(reservationRepository.findAll(spec, pageable),
                CreateReservationResponseDTO::from);
    }

    /**
     * Mapa de butacas de una función.
     *
     * <p>Distingue tres situaciones: libre, vendida y bloqueada por otro cliente. La última no es
     * una venta, pero para quien mira el mapa se comporta como tal, y decirlo evita que elija una
     * butaca que casi con seguridad va a perder.
     *
     * @param viewerEmail quien consulta, o {@code null} si no está autenticado; sus propios
     *                    bloqueos no se marcan, porque para esa persona esas butacas están libres
     */
    public SeatMapResponseDTO findSeatMap(UUID screeningUuid, String viewerEmail) {
        MovieScreening screening = movieScreeningService.getScreening(screeningUuid);
        Set<UUID> booked = new HashSet<>(ticketRepository.findBookedSeatIds(screeningUuid));
        UUID viewerUuid = viewerEmail == null ? null : userService.findUuidByEmail(viewerEmail);
        Set<UUID> held = new HashSet<>(seatHoldService.findHeldSeatIds(screeningUuid, viewerUuid));
        List<Seat> seats = seatRepository.findByScreenRoomUuid(screening.getScreenRoom().getUuid());

        return SeatMapResponseDTO.of(
                screening,
                distinctSorted(seats, seat -> seat.getPosition().row()),
                distinctSorted(seats, seat -> seat.getPosition().column()),
                seats.stream()
                        .map(seat -> SeatAvailabilityResponseDTO.of(
                                seat,
                                !booked.contains(seat.getUuid()) && !held.contains(seat.getUuid()),
                                held.contains(seat.getUuid())))
                        .toList());
    }

    private Reservation getReservation(UUID uuid) {
        return reservationRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Reservation", uuid));
    }
}
