package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Producto del candy bar comprado junto con las entradas. El nombre y el precio se copian al
 * momento de la compra para que una edición posterior del catálogo no reescriba la historia.
 */
@Entity
@Table(
        name = "reservation_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reservation_item_product",
                columnNames = {"reservation_id", "inventory_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"reservation", "inventory"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;
}
