-- Cancelación por transición de estado en lugar de por borrado.
--
-- Hasta acá, cancelar borraba las entradas y las líneas de confitería: la reserva devuelta quedaba
-- vacía y no había forma de auditar qué se había cancelado. A partir de ahora las filas se
-- conservan y se marcan.
ALTER TABLE reservation
    ADD COLUMN cancelled_at         TIMESTAMPTZ,
    ADD COLUMN original_total_price DECIMAL(10, 2);

ALTER TABLE ticket
    ADD COLUMN cancelled_at TIMESTAMPTZ;

-- La unicidad (función, butaca) tiene que dejar de contar las entradas liberadas, o una butaca
-- cancelada no podría volver a venderse. Un índice único parcial expresa exactamente esa regla y
-- sigue siendo la barrera contra la doble venta bajo concurrencia.
ALTER TABLE ticket
    DROP CONSTRAINT uk_ticket_screening_seat;

CREATE UNIQUE INDEX uk_ticket_screening_seat
    ON ticket (movie_screening_id, seat_id)
    WHERE cancelled_at IS NULL;
