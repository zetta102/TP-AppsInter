package tp.appsinter.cine.reservation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.NullUnmarked;
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.user.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@NullUnmarked
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "movieScreening", "tickets", "items"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Reservation {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_screening_id", nullable = false)
    private MovieScreening movieScreening;

    /*
     * Ambas colecciones se cargan en lotes: una página de reservas las necesita todas, y sin
     * @BatchSize dispararía dos consultas por reserva.
     */
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<ReservationItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Momento de la cancelación, o nulo si la reserva sigue vigente.
     */
    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    /**
     * Importe que la reserva tenía antes de cancelarse.
     *
     * <p>Al cancelar, {@code totalPrice} pasa a ser lo que se debe —cero— y este campo conserva lo
     * que se dejó de cobrar, que es el dato que necesita cualquier informe de ingresos.
     */
    @Column(name = "original_total_price", precision = 10, scale = 2)
    private BigDecimal originalTotalPrice;

    @Version
    private Long version;

    public void addTicket(Ticket ticket) {
        tickets.add(ticket);
        ticket.setReservation(this);
    }

    public void addItem(ReservationItem item) {
        items.add(item);
        item.setReservation(this);
    }
}
