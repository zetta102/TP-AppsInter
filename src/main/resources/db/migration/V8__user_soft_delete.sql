-- Baja lógica de usuarios.
--
-- Un usuario no puede borrarse de verdad: sus reservas son parte del historial de ventas y perderían
-- el titular. Se marca la fecha de baja, se le impide iniciar sesión y se conserva todo lo demás.
ALTER TABLE app_user
    ADD COLUMN deleted_at TIMESTAMPTZ;

-- Las consultas de administración casi siempre excluyen a los dados de baja.
CREATE INDEX idx_app_user_deleted_at ON app_user (deleted_at);
