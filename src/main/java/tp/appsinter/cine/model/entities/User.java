package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;

import java.util.List;
import java.util.UUID;

@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    @OneToMany
    private List<Reservation> reservationList;
}
