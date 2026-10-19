package tp.appsinter.cine.contact;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.NullUnmarked;
import tp.appsinter.cine.user.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mensaje enviado por un cliente desde el formulario de contacto.
 *
 * <p>El autor puede corregirlo mientras nadie de administración lo haya leído. Una vez leído queda
 * fijo: lo que se responda tiene que seguir correspondiendo a lo que se preguntó.
 */
@Entity
@NullUnmarked
@Table(name = "contact_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ContactMessage {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ContactCategory category;

    @Column(nullable = false, length = 150)
    private String subject;

    @Column(nullable = false, length = 5000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Última corrección del autor, o {@code null} si nunca se editó.
     */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Momento en que administración lo abrió por primera vez, o {@code null} si sigue sin leer.
     */
    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}
