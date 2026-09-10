-- Ventana de gracia en la rotación de tokens de refresco.
--
-- Hasta acá, refrescar revocaba el token usado: dos pestañas refrescando a la vez, o una respuesta
-- que se pierde después de que el servidor confirmó, cerraban la sesión. Ahora el token se marca
-- como rotado y se sigue aceptando un rato corto. Pasada la ventana, volver a presentarlo sí es
-- reutilización y se revoca toda la familia.
ALTER TABLE refresh_token
    ADD COLUMN rotated_at TIMESTAMPTZ,
    ADD COLUMN family_id  UUID;

-- Los tokens ya emitidos son cada uno su propia familia: no hay historia de rotación que reconstruir.
UPDATE refresh_token
SET family_id = uuid
WHERE family_id IS NULL;

ALTER TABLE refresh_token
    ALTER COLUMN family_id SET NOT NULL;

-- Revocar una familia entera es la operación de la detección de robo; sin índice recorrería la tabla.
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
