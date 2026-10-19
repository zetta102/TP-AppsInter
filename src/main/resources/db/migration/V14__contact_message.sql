-- Mensajes del formulario de contacto.
--
-- El autor es siempre una cuenta registrada; nombre y correo se leen de app_user. read_at marca la
-- primera apertura por administración, y a partir de ahí el autor ya no puede editar el mensaje.
CREATE TABLE contact_message
(
    uuid       UUID          NOT NULL,
    user_id    UUID          NOT NULL,
    category   VARCHAR(30)   NOT NULL,
    subject    VARCHAR(150)  NOT NULL,
    body       VARCHAR(5000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ,
    read_at    TIMESTAMPTZ,
    CONSTRAINT pk_contact_message PRIMARY KEY (uuid),
    CONSTRAINT fk_contact_message_user FOREIGN KEY (user_id) REFERENCES app_user (uuid) ON DELETE CASCADE
);

-- «Mis mensajes» filtra por autor; la bandeja de administración ordena por fecha.
CREATE INDEX idx_contact_message_user_created ON contact_message (user_id, created_at);
CREATE INDEX idx_contact_message_created ON contact_message (created_at);
