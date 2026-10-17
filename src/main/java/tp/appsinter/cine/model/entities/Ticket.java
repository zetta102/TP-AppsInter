package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import tp.appsinter.cine.model.utils.SeatType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una butaca vendida para una función.
 *
 * <p>La garantía contra la doble venta es el índice único parcial
 * {@code uk_ticket_screening_seat (movie_screening_id, seat_id) where cancelled_at is null}, definido
 * en la migración. No se declara acá porque JPA sólo sabe expresar restricciones únicas totales, y
 * una total impediría volver a vender una butaca liberada por una cancelación.
 */
@Entity
@Table(name = "ticket")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"reservation", "movieScreening", "seat"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Ticket {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_screening_id", nullable = false)
    private MovieScreening movieScreening;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Momento en que la entrada quedó liberada por una cancelación. Mientras sea nulo, la butaca
     * cuenta como ocupada.
     */
    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    public boolean isCancelled() {
        return cancelledAt != null;
    }
}
