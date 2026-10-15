-- Endurecimiento de integridad.
--
-- Varias reglas vivían sólo en la aplicación como «comprobar y después escribir», que no resiste a
-- dos peticiones simultáneas. Acá pasan a la base, que es la única que puede garantizarlas.

-- Las columnas de @Version nacieron anulables. Hibernate no sabe actualizar una fila con versión
-- nula: la condición "version = ?" nunca coincide y la escritura falla como conflicto.
UPDATE reservation SET version = 0 WHERE version IS NULL;
ALTER TABLE reservation
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

UPDATE inventory SET version = 0 WHERE version IS NULL;
ALTER TABLE inventory
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

-- El ajuste atómico ya impide el stock negativo, pero la venta y la edición escriben el valor
-- absoluto; la base es la última barrera.
ALTER TABLE inventory
    ADD CONSTRAINT ck_inventory_quantity CHECK (quantity >= 0);

-- La aplicación compara correos sin distinguir mayúsculas; la unicidad tiene que hacer lo mismo o
-- dos altas simultáneas con distinto uso de mayúsculas pasan las dos.
ALTER TABLE app_user
    DROP CONSTRAINT uk_app_user_email;
CREATE UNIQUE INDEX uk_app_user_email_lower ON app_user (lower(email));

-- Dos funciones de la misma sala no pueden superponerse. La comprobación previa sigue existiendo
-- para poder nombrar la función con la que choca; esto cubre la carrera entre dos altas.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE movie_screening
    ADD CONSTRAINT ex_movie_screening_room_overlap
        EXCLUDE USING gist (screen_room_id WITH =, tstzrange(starts_at, ends_at) WITH &&);

-- La vista de administración filtra y ordena por fecha de compra.
CREATE INDEX idx_reservation_created_at ON reservation (created_at);
