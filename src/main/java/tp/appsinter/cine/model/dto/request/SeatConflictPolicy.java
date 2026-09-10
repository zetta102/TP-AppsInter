package tp.appsinter.cine.model.dto.request;

/**
 * Qué hacer cuando una butaca del lote ya existe en esa posición de la sala.
 */
public enum SeatConflictPolicy {

    /**
     * Aborta todo el lote con {@code 409} y la lista de posiciones en conflicto. Es el
     * comportamiento por omisión porque un choque suele significar que el lote está mal armado, y
     * aplicarlo a medias dejaría la sala en un estado que nadie pidió.
     */
    FAIL,

    /**
     * Deja intactas las butacas existentes y crea el resto. Sirve para completar una sala a la que
     * le faltan filas.
     */
    SKIP,

    /**
     * Actualiza el tipo de las butacas existentes. Sirve para reetiquetar una zona como premium sin
     * tener que borrarla primero.
     */
    REPLACE
}
