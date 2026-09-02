package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;
import tp.appsinter.cine.model.utils.SeatType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single booked seat for a single screening. The unique constraint on
 * (movie_screening_id, seat_id) is the authoritative guard against double-booking.
 */
@Entity
@Table(
        name = "ticket",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ticket_screening_seat",
                columnNames = {"movie_screening_id", "seat_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"reservation", "movieScreening", "seat"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
}
