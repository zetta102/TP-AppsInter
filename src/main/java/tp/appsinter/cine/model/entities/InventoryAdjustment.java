package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un movimiento de stock asentado.
 *
 * <p>El stock se fijaba en valor absoluto, así que una diferencia entre lo contado y lo registrado
 * no tenía explicación posible. Guardar cada ajuste con su motivo convierte esa diferencia en algo
 * que se puede rastrear.
 */
@Entity
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
    @GeneratedValue(strategy = GenerationType.UUID)
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
     * Correo de quien lo hizo. Puede faltar en ajustes de origen automático.
     */
    @Column(name = "adjusted_by")
    private String adjustedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
