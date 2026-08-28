package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class MovieScreening {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    @OneToOne
    private Movie movie;
    private LocalDateTime from;
    private LocalDateTime to;
}
