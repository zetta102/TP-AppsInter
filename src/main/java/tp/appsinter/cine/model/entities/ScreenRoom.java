package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import tp.appsinter.cine.model.utils.ScreenRoomType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "screen_room")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "seats")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ScreenRoom {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @Column(nullable = false, unique = true)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScreenRoomType screenRoomType;

    @OneToMany(mappedBy = "screenRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();

    public void addSeat(Seat seat) {
        seats.add(seat);
        seat.setScreenRoom(this);
    }
    /**
     * Momento en que se archivó, o {@code null} si está vigente. Archivado no es borrado: deja de
     * ofrecerse, pero sigue resolviéndose por identificador.
     */
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    public boolean isArchived() {
        return archivedAt != null;
    }
}
