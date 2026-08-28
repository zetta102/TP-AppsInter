package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;

import java.util.List;
import java.util.UUID;

@Entity
public class ScreenRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    private String roomName;
    @OneToMany
    private List<Seat> seats;
}
