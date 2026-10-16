package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.model.utils.SeatType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Presupuesto de una reserva calculado por el servidor, sin efectos sobre el stock ni sobre las
 * butacas.
 *
 * <p>Existe para que el total que ve el cliente antes de pagar lo calcule la misma aritmética que
 * después lo cobra. Los subtotales van desglosados porque la pantalla de pago los muestra por
 * separado y no debería tener que volver a sumarlos.
 */
@Schema(name = "Quote")
public record ReservationQuoteResponseDTO(
        List<QuotedTicket> tickets,
        List<QuotedItem> items,
        BigDecimal ticketsTotal,
        BigDecimal itemsTotal,
        BigDecimal totalPrice
) {

    public record QuotedTicket(
            UUID seatId,
            String row,
            String column,
            SeatType seatType,
            BigDecimal price
    ) {
    }

    public record QuotedItem(
            UUID inventoryId,
            String productName,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineTotal
    ) {
    }
}
