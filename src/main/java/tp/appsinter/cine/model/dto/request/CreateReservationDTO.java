package tp.appsinter.cine.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Petición de alta de una reserva.
 *
 * @param holdId bloqueo temporal del que provienen las butacas, si el cliente creó uno. Es opcional:
 *               sin él la compra sigue funcionando, sólo que expuesta a que otro se adelante.
 */
public record CreateReservationDTO(
        @NotNull UUID movieScreeningId,
        @NotEmpty List<UUID> seatIds,
        @Valid List<ReservationItemRequestDTO> products,
        UUID holdId
) {
    public CreateReservationDTO {
        seatIds = seatIds == null ? List.of() : seatIds.stream().distinct().toList();
        products = products == null ? List.of() : mergeByProduct(products);
    }

    /**
     * Compra sin bloqueo previo, que es el camino corto y sigue siendo válido.
     */
    public CreateReservationDTO(UUID movieScreeningId,
                                List<UUID> seatIds,
                                List<ReservationItemRequestDTO> products) {
        this(movieScreeningId, seatIds, products, null);
    }

    /**
     * Repetir el mismo producto se interpreta como pedir más unidades, no como un error.
     */
    private static List<ReservationItemRequestDTO> mergeByProduct(List<ReservationItemRequestDTO> products) {
        var merged = new LinkedHashMap<UUID, Integer>();
        for (ReservationItemRequestDTO product : products) {
            if (product == null || product.inventoryId() == null || product.quantity() == null) {
                continue;
            }
            merged.merge(product.inventoryId(), product.quantity(), Integer::sum);
        }
        return merged.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> new ReservationItemRequestDTO(entry.getKey(), entry.getValue()))
                .toList();
    }
}
