package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;

import java.util.List;
import java.util.UUID;

@Entity
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    @OneToOne
    private User user;
    @OneToMany
    private List<Seat> seats;
    @OneToOne
    private MovieScreening movieScreening;
}
