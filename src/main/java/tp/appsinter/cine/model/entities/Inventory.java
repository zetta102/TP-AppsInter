package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;
import tp.appsinter.cine.model.utils.ProductCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Producto del candy bar que se vende en el mostrador.
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @Column(nullable = false, unique = true)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductCategory category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false)
    private Integer quantity;

    @Version
    private Long version;

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
