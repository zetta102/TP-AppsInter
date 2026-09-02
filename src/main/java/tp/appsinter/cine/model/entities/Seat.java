package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;
import tp.appsinter.cine.model.utils.Position;
import tp.appsinter.cine.model.utils.SeatType;

import java.util.UUID;

@Entity
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
    @GeneratedValue(strategy = GenerationType.UUID)
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
