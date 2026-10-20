package tp.appsinter.cine.inventory;

import java.util.UUID;

/**
 * Lo que el borrado de un producto necesita saber de sus ventas.
 *
 * <p>Lo implementa {@code reservation}, que depende de {@code inventory} y no al revés.
 */
public interface ProductSales {

    /**
     * Indica si el producto figura en alguna línea de reserva, vigente o cancelada: ambas son
     * historial.
     */
    boolean hasSales(UUID inventoryUuid);
}
