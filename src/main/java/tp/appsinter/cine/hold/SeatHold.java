package tp.appsinter.cine.hold;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.NullUnmarked;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.user.User;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reserva temporal de butacas mientras un cliente completa su compra.
 *
 * <p>Un bloqueo vigente hace que sus butacas figuren como no disponibles para todos menos para su
 * dueño. No es una entrada: no garantiza nada más allá de su vencimiento, y si el cliente abandona
 * la compra las butacas vuelven a la venta sin que nadie tenga que intervenir.
 *
 * <p>El vencimiento se evalúa siempre contra {@code expiresAt} y nunca contra la existencia de la
 * fila: un barrido demorado no debe poder impedir una venta.
 */
@Entity
@NullUnmarked
@Table(name = "seat_hold")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"movieScreening", "user", "seats"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SeatHold {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_screening_id", nullable = false)
    private MovieScreening movieScreening;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "seat_hold_seat",
            joinColumns = @JoinColumn(name = "seat_hold_id"),
            inverseJoinColumns = @JoinColumn(name = "seat_id")
    )
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /**
     * Momento en que el bloqueo se soltó antes de tiempo, sea porque el cliente lo canceló o porque
     * su compra se confirmó.
     */
    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public boolean isLive(OffsetDateTime at) {
        return releasedAt == null && expiresAt.isAfter(at);
    }
}
