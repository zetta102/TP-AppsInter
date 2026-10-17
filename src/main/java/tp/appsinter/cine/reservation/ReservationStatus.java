package tp.appsinter.cine.reservation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Estado de una reserva.
 */
@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
    /**
     * Reservado y sin uso.
     *
     * <p>Describiría una reserva creada pero todavía no pagada. Como el cobro está fuera del alcance
     * del sistema, ninguna operación produce este estado: la reserva nace {@code CONFIRMED}. Se
     * mantiene en el contrato para no romper a los clientes que ya lo conocen.
     */
    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    CANCELLED("Cancelled");

    private final String displayName;
}
