package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un token de refresco opaco. Sólo se persiste su hash SHA-256, de modo que una filtración de la
 * base no entrega tokens usables.
 */
@Entity
@Table(
        name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user", "tokenHash"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    /**
     * Momento en que este token se usó para refrescar, o {@code null} si sigue sin usarse.
     *
     * <p>Un token rotado no se revoca de inmediato: se lo sigue aceptando durante la ventana de
     * gracia para tolerar refrescos simultáneos y respuestas perdidas.
     */
    @Column(name = "rotated_at")
    private OffsetDateTime rotatedAt;

    /**
     * Cadena de rotaciones a la que pertenece el token. El primero de la cadena se apadrina a sí
     * mismo. Detectar reutilización fuera de la ventana revoca la familia entera, que es lo que
     * corta el acceso a quien robó el token sin depender de saber cuál de los dos es el ladrón.
     */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Token utilizable sin reservas: ni revocado, ni rotado, ni vencido.
     */
    public boolean isActive(OffsetDateTime now) {
        return revokedAt == null && rotatedAt == null && expiresAt.isAfter(now);
    }

    /**
     * Token ya rotado pero todavía aceptable, porque la rotación ocurrió hace menos que la ventana
     * de gracia.
     */
    public boolean isWithinGrace(OffsetDateTime now, Duration grace) {
        return revokedAt == null
                && rotatedAt != null
                && expiresAt.isAfter(now)
                && rotatedAt.plus(grace).isAfter(now);
    }
}
