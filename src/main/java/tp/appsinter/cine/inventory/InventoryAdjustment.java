package tp.appsinter.cine.inventory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.NullUnmarked;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un movimiento manual de stock asentado: la carga inicial del producto, una reposición, una baja o
 * una corrección.
 *
 * <p>Guardar cada movimiento con su motivo y su autor permite explicar una diferencia entre lo
 * contado y lo registrado. Las ventas y cancelaciones no se asientan acá: constan en las líneas de
 * cada reserva.
 */
@Entity
@NullUnmarked
@Table(name = "inventory_adjustment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "inventory")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class InventoryAdjustment {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    /**
     * Variación aplicada. Positiva en una recepción, negativa en una merma o una corrección.
     */
    @Column(nullable = false)
    private Integer delta;

    /**
     * Stock que quedó después del ajuste. Se guarda para poder leer el historial sin recalcular la
     * suma acumulada desde el principio.
     */
    @Column(name = "resulting_stock", nullable = false)
    private Integer resultingStock;

    @Column(nullable = false)
    private String reason;

    /**
     * Correo de quien lo hizo, o {@code null} si no lo hizo una persona con cuenta (la carga de
     * datos de demostración, por ejemplo).
     */
    @Column(name = "adjusted_by")
    private String adjustedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
