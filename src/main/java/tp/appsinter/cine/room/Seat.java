package tp.appsinter.cine.room;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.NullUnmarked;

import java.util.UUID;

/**
 * Una butaca de una sala. Se carga en lotes ({@code @BatchSize}) cuando se navega desde las
 * entradas de una página de reservas, en lugar de una consulta por entrada.
 */
@Entity
@NullUnmarked
@BatchSize(size = 50)
@Table(
        name = "seat",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seat_room_position",
                columnNames = {"screen_room_id", "seat_row", "seat_column"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "screenRoom")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Seat {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @Embedded
    private Position position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatType seatType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_room_id", nullable = false)
    private ScreenRoom screenRoom;
}
