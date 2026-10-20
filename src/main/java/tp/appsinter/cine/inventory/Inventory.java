package tp.appsinter.cine.inventory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.NullUnmarked;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Producto de la confitería que se vende en el mostrador.
 */
@Entity
@NullUnmarked
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
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @EqualsAndHashCode.Include
    private UUID uuid;

    /**
     * Único sin distinguir mayúsculas: lo garantiza el índice {@code uk_inventory_product_name_lower}.
     */
    @Column(nullable = false)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductCategory category;

    /**
     * Precio de venta al público. Cada línea de reserva lo copia al momento de la compra.
     */
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
