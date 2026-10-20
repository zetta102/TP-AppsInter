package tp.appsinter.cine.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.hold.SeatHoldService;
import tp.appsinter.cine.inventory.Inventory;
import tp.appsinter.cine.inventory.InventoryRepository;
import tp.appsinter.cine.reservation.dto.request.MyReservationFilterDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationFilterDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationItemRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationRequestDTO;
import tp.appsinter.cine.reservation.dto.response.QuoteResponseDTO;
import tp.appsinter.cine.reservation.dto.response.ReservationResponseDTO;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.screening.MovieScreeningService;
import tp.appsinter.cine.screening.SeatsChangedEvent;
import tp.appsinter.cine.shared.BusinessConstants;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.shared.persistence.DatabaseConstraints;
import tp.appsinter.cine.user.User;
import tp.appsinter.cine.user.UserService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReservationService {

    /**
     * Índice único parcial de {@code V1__schema.sql} que impide vender dos veces la misma butaca.
     */
    private static final String DOUBLE_BOOKING_CONSTRAINT = "uk_ticket_screening_seat";

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final MovieScreeningService movieScreeningService;
    private final UserService userService;
    private final SeatHoldService seatHoldService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    static BigDecimal priceFor(BigDecimal basePrice, SeatType seatType) {
        BigDecimal multiplier = switch (seatType) {
            case NORMAL -> BigDecimal.ONE;
            case PREMIUM -> BusinessConstants.PREMIUM_SEAT_MULTIPLIER;
        };
        return basePrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Compra butacas y confitería.
     *
     * <p>El stock de confitería se descuenta en memoria bajo {@code @Version}: si otra compra tocó el
     * mismo producto entre la lectura y la escritura, el intento entero se deshace y se repite con el
     * stock fresco. Sólo cuando los reintentos se agotan llega al cliente {@code OPTIMISTIC_LOCK}.
     *
     * <p>La compra suelta los bloqueos de quien compra que incluían esas butacas, y avisa también de
     * las butacas que esos bloqueos apartaban y no se compraron, que vuelven a estar libres.
     */
    @Retryable(includes = OptimisticLockingFailureException.class,
            maxRetries = 3, delay = 50, jitter = 25, multiplier = 2)
    @Transactional
    public ReservationResponseDTO createReservation(String userEmail, ReservationRequestDTO request) {
        User user = userService.getByEmail(userEmail);
        // Serializa la compra con los bloqueos de la misma función: la comprobación de bloqueos
        // ajenos de más abajo no está respaldada por ningún índice único.
        MovieScreening screening = movieScreeningService.lockScreening(request.movieScreeningId());
        OffsetDateTime now = OffsetDateTime.now(clock);
        PricedBasket basket = price(screening, request, now);
        requireHoldsAllow(request, user);

        Reservation reservation = Reservation.builder()
                .user(user)
                .movieScreening(screening)
                .status(ReservationStatus.CONFIRMED)
                .createdAt(now)
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

        Set<UUID> changed = new HashSet<>(request.seatIds());
        changed.addAll(seatHoldService.releaseForPurchase(user, screening.getUuid(), request.seatIds()));
        try {
            // Se guarda dentro de la transacción para que la restricción única (función, butaca)
            // aflore aquí y no al confirmar.
            Reservation saved = reservationRepository.saveAndFlush(reservation);
            events.publishEvent(new SeatsChangedEvent(screening.getUuid(), changed));
            return ReservationResponseDTO.from(saved);
        } catch (DataIntegrityViolationException ex) {
            if (!DatabaseConstraints.violates(ex, DOUBLE_BOOKING_CONSTRAINT)) {
                throw ex;
            }
            log.debug("Concurrent booking rejected for screening {}", screening.getUuid(), ex);
            throw ConflictException.seatsUnavailable(
                    "One or more seats were just booked by another customer", request.seatIds());
        }
    }

    /**
     * Calcula lo que costaría una reserva sin crearla.
     *
     * <p>Recorre exactamente la misma cadena de validación que el alta, incluidos los bloqueos de
     * butacas, así que un presupuesto que responde 200 es también una reserva que se puede confirmar
     * —salvo que alguien se adelante a tomar las butacas— y los mismos 404, 409 y 422 aparecen acá
     * primero. El {@code holdId}, si viene, se comprueba pero no se consume.
     */
    public QuoteResponseDTO quote(String userEmail, ReservationRequestDTO request) {
        User user = userService.getByEmail(userEmail);
        MovieScreening screening = movieScreeningService.getScreening(request.movieScreeningId());
        PricedBasket basket = price(screening, request, OffsetDateTime.now(clock));
        requireHoldsAllow(request, user);
        return new QuoteResponseDTO(
                basket.seats().stream()
                        .map(seat -> new QuoteResponseDTO.QuotedTicket(
                                seat.seat().getUuid(),
                                seat.seat().getPosition().row(),
                                seat.seat().getPosition().column(),
                                seat.seat().getSeatType(),
                                seat.price()))
                        .toList(),
                basket.products().stream()
                        .map(product -> new QuoteResponseDTO.QuotedItem(
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
     * Últimas comprobaciones de la cadena, compartidas por presupuesto y alta: el bloqueo indicado
     * tiene que servir para esta compra, y ninguna butaca puede estar bloqueada por otro cliente.
     * Los bloqueos propios nunca cuentan como ajenos.
     */
    private void requireHoldsAllow(ReservationRequestDTO request, User buyer) {
        if (request.holdId() != null) {
            seatHoldService.requireUsable(request.holdId(), buyer.getEmail(), request.movieScreeningId(),
                    request.seatIds());
        }
        seatHoldService.requireNotHeldByOthers(request.movieScreeningId(), request.seatIds(), buyer.getUuid());
    }

    /**
     * Valida un pedido y le pone precio, sin tocar nada.
     *
     * <p>Es el único lugar donde vive la cadena de reglas de la compra, para que presupuestar y
     * confirmar no puedan discrepar. Las comprobaciones van de la más barata a la más cara y de la
     * más general a la más específica: primero las de la función y sus butacas, compartidas con los
     * bloqueos ({@link MovieScreeningService#requireBookableSeats}), y recién al final la
     * confitería, que tampoco puede estar archivada. Los bloqueos ajenos los comprueba cada llamador.
     */
    private PricedBasket price(MovieScreening screening, ReservationRequestDTO request, OffsetDateTime now) {
        List<Seat> seats = movieScreeningService.requireBookableSeats(screening, request.seatIds(), now);
        List<PricedSeat> pricedSeats = seats.stream()
                .map(seat -> new PricedSeat(seat, priceFor(screening.getBasePrice(), seat.getSeatType())))
                .toList();
        return new PricedBasket(pricedSeats, priceProducts(Objects.requireNonNull(request.products())));
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
                .collect(Collectors.toMap(Inventory::getUuid, Function.identity()));
        if (byId.size() != productIds.size()) {
            throw NotFoundException.withMessage("Product", "One or more requested products do not exist");
        }

        List<PricedProduct> priced = new ArrayList<>(products.size());
        for (ReservationItemRequestDTO product : products) {
            Inventory inventory = Objects.requireNonNull(byId.get(product.inventoryId()));
            if (inventory.isArchived()) {
                throw BusinessRuleException.resourceArchived("Product", inventory.getUuid());
            }
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
    private record PricedBasket(List<PricedSeat> seats, List<PricedProduct> products) {

        BigDecimal ticketsTotal() {
            return sum(seats.stream().map(PricedSeat::price));
        }

        BigDecimal itemsTotal() {
            return sum(products.stream().map(PricedProduct::lineTotal));
        }

        BigDecimal totalPrice() {
            return ticketsTotal().add(itemsTotal());
        }

        private static BigDecimal sum(Stream<BigDecimal> amounts) {
            return amounts.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Cancela una reserva conservando su historia.
     *
     * <p>Las entradas y las líneas de confitería no se borran: se marcan con la fecha de
     * cancelación. Así el cliente puede ver qué canceló, la administración puede auditarlo y los
     * informes de ingresos siguen teniendo con qué trabajar. Lo único que se pierde es la ocupación
     * de las butacas, que vuelven a estar a la venta porque el índice único parcial ignora las
     * entradas liberadas.
     *
     * <p>Un cliente no puede cancelar una vez que la función empezó: ya no hay butaca que devolver a
     * la venta y la cancelación sólo serviría para anular el cobro. La administración sí puede,
     * porque es quien resuelve reclamos.
     *
     * <p>Devolver la confitería es la misma lectura-escritura bajo {@code @Version} que la compra, y
     * se reintenta igual ante una escritura concurrente.
     */
    @Retryable(includes = OptimisticLockingFailureException.class,
            maxRetries = 3, delay = 50, jitter = 25, multiplier = 2)
    @Transactional
    public ReservationResponseDTO cancelReservation(UUID uuid, String userEmail, boolean isAdmin) {
        Reservation reservation = getReservation(uuid);
        if (!isAdmin && !reservation.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw NotFoundException.of("Reservation", uuid);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw BusinessRuleException.alreadyCancelled(uuid);
        }
        OffsetDateTime cancelledAt = OffsetDateTime.now(clock);
        MovieScreening screening = reservation.getMovieScreening();
        if (!isAdmin && screening.getStartsAt().isBefore(cancelledAt)) {
            throw BusinessRuleException.screeningStarted(screening.getUuid(), screening.getStartsAt());
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(cancelledAt);
        // totalPrice pasa a significar "lo que se debe"; lo que se dejó de cobrar queda aparte.
        reservation.setOriginalTotalPrice(reservation.getTotalPrice());
        reservation.setTotalPrice(BigDecimal.ZERO);
        reservation.getTickets().forEach(ticket -> ticket.setCancelledAt(cancelledAt));
        // La confitería vuelve a la góndola.
        reservation.getItems().forEach(item -> {
            item.setCancelledAt(cancelledAt);
            Inventory inventory = item.getInventory();
            inventory.setQuantity(inventory.getQuantity() + item.getQuantity());
        });
        Reservation saved = reservationRepository.saveAndFlush(reservation);
        events.publishEvent(new SeatsChangedEvent(screening.getUuid(),
                reservation.getTickets().stream().map(ticket -> ticket.getSeat().getUuid()).toList()));
        return ReservationResponseDTO.from(saved);
    }

    public ReservationResponseDTO findById(UUID uuid, String userEmail, boolean isAdmin) {
        Reservation reservation = getReservation(uuid);
        if (!isAdmin && !reservation.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw NotFoundException.of("Reservation", uuid);
        }
        return ReservationResponseDTO.from(reservation);
    }

    /**
     * Historial del cliente autenticado, restringido a sus propias reservas.
     *
     * <p>El filtro de titular se aplica acá y no en el controlador: así ningún parámetro de consulta
     * puede ampliarlo a las reservas de otra persona.
     */
    public PageResponseDTO<ReservationResponseDTO> findMine(String userEmail,
                                                            MyReservationFilterDTO filter,
                                                            Pageable pageable) {
        Specification<Reservation> spec = ReservationSpecifications.withAssociations()
                .and(ReservationSpecifications.ownedBy(userEmail))
                .and(ReservationSpecifications.hasStatus(filter.status()))
                .and(ReservationSpecifications.hasMovie(filter.movieId()))
                .and(ReservationSpecifications.hasScreening(filter.screeningId()))
                .and(ReservationSpecifications.upcoming(filter.upcoming(), OffsetDateTime.now(clock)))
                .and(ReservationSpecifications.screeningBetween(filter.from(), filter.to()));
        return PageResponseDTO.from(reservationRepository.findAll(spec, pageable),
                ReservationResponseDTO::from);
    }

    /**
     * Vista de administración sobre todos los clientes.
     *
     * <p>Acá {@code from}/{@code to} acotan la fecha de compra, no la de la función: un operador
     * suele reconstruir lo vendido en una jornada.
     */
    public PageResponseDTO<ReservationResponseDTO> findAll(ReservationFilterDTO filter, Pageable pageable) {
        Specification<Reservation> spec = ReservationSpecifications.withAssociations()
                .and(ReservationSpecifications.hasStatus(filter.status()))
                .and(ReservationSpecifications.hasUser(filter.userId()))
                .and(ReservationSpecifications.emailContains(filter.email()))
                .and(ReservationSpecifications.hasScreening(filter.screeningId()))
                .and(ReservationSpecifications.hasMovie(filter.movieId()))
                .and(ReservationSpecifications.upcoming(filter.upcoming(), OffsetDateTime.now(clock)))
                .and(ReservationSpecifications.createdBetween(filter.from(), filter.to()));
        return PageResponseDTO.from(reservationRepository.findAll(spec, pageable),
                ReservationResponseDTO::from);
    }

    private Reservation getReservation(UUID uuid) {
        return reservationRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Reservation", uuid));
    }
}
