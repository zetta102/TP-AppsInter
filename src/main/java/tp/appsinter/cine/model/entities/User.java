package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import tp.appsinter.cine.model.utils.Role;

import java.util.ArrayList;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"password", "reservations"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.CUSTOMER;

    @OneToMany(mappedBy = "user")
    @Builder.Default
    private List<Reservation> reservations = new ArrayList<>();

    /**
     * Fecha de baja lógica, o {@code null} si la cuenta está activa.
     *
     * <p>No se borra la fila porque sus reservas son historial de ventas y quedarían huérfanas. Una
     * cuenta dada de baja no puede iniciar sesión ni aparecer en los listados.
     */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
