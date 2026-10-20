-- Esquema completo de la base.
--
-- Varias reglas no pueden vivir sólo en la aplicación como «comprobar y después escribir», porque no
-- resisten a dos peticiones simultáneas: la doble venta de una butaca, la superposición de funciones,
-- el stock negativo o el correo repetido. Acá las garantiza la base.

-- Las columnas que guardan un enum de la aplicación llevan un CHECK con sus valores exactos: un valor
-- desconocido haría fallar la lectura de la fila entera, así que es mejor rechazarlo al escribir.

-- Necesaria para combinar igualdad y superposición de rangos en una restricción EXCLUDE.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Usuarios ------------------------------------------------------------------------------------------

-- Baja lógica: un usuario no puede borrarse de verdad porque sus reservas son parte del historial de
-- ventas y perderían el titular. Se marca deleted_at, se le impide iniciar sesión y se conserva todo.
CREATE TABLE app_user
(
    uuid       UUID         NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT pk_app_user PRIMARY KEY (uuid),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

-- La aplicación compara correos sin distinguir mayúsculas; la unicidad tiene que hacer lo mismo o
-- dos altas simultáneas con distinto uso de mayúsculas pasan las dos.
CREATE UNIQUE INDEX uk_app_user_email_lower ON app_user (lower(email));
-- Las consultas de administración casi siempre excluyen a los dados de baja.
CREATE INDEX idx_app_user_deleted_at ON app_user (deleted_at);

-- Películas -----------------------------------------------------------------------------------------

-- Archivado en lugar de borrado (vale también para salas, funciones y productos): lo que tiene
-- historia no puede borrarse, pero sí dejar de ofrecerse. Un recurso archivado desaparece de los
-- listados por omisión y no admite operaciones nuevas, pero sigue resolviéndose por identificador
-- para que las reservas viejas no se rompan.
CREATE TABLE movie
(
    uuid              UUID          NOT NULL,
    movie_name        VARCHAR(255)  NOT NULL,
    runtime           INTEGER       NOT NULL,
    description       VARCHAR(2000),
    director          VARCHAR(255),
    -- Campos de presentación del catálogo, todos opcionales: sin ellos el frontend muestra su
    -- marcador de posición.
    poster_url        VARCHAR(2048),
    backdrop_url      VARCHAR(2048),
    release_year      INTEGER,
    certification     VARCHAR(16),
    original_language VARCHAR(8),
    trailer_url       VARCHAR(2048),
    archived_at       TIMESTAMPTZ,
    CONSTRAINT pk_movie PRIMARY KEY (uuid),
    CONSTRAINT ck_movie_runtime CHECK (runtime > 0)
);

-- El filtro por año recorre el catálogo entero en la pantalla de búsqueda.
CREATE INDEX idx_movie_release_year ON movie (release_year);

CREATE TABLE movie_genre
(
    movie_id UUID        NOT NULL,
    genre    VARCHAR(30) NOT NULL,
    -- Un género repetido no agrega nada. La clave empieza por movie_id y sirve también para buscar
    -- los géneros de una película.
    CONSTRAINT pk_movie_genre PRIMARY KEY (movie_id, genre),
    CONSTRAINT fk_movie_genre_movie FOREIGN KEY (movie_id) REFERENCES movie (uuid) ON DELETE CASCADE,
    CONSTRAINT ck_movie_genre_genre
        CHECK (genre IN ('ACTION', 'THRILLER', 'TERROR', 'ROMANCE', 'COMEDY', 'DRAMA', 'ANIMATION', 'SCI_FI'))
);

CREATE TABLE movie_main_cast
(
    movie_id UUID         NOT NULL,
    actor    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_movie_main_cast_movie FOREIGN KEY (movie_id) REFERENCES movie (uuid) ON DELETE CASCADE
);

CREATE INDEX idx_movie_main_cast_movie ON movie_main_cast (movie_id);

-- Salas y butacas -----------------------------------------------------------------------------------

CREATE TABLE screen_room
(
    uuid             UUID         NOT NULL,
    room_name        VARCHAR(255) NOT NULL,
    screen_room_type VARCHAR(20)  NOT NULL,
    archived_at      TIMESTAMPTZ,
    CONSTRAINT pk_screen_room PRIMARY KEY (uuid),
    CONSTRAINT ck_screen_room_type CHECK (screen_room_type IN ('NORMAL', 'THREED', 'FOURD', 'IMAX'))
);

-- La aplicación compara nombres sin distinguir mayúsculas, igual que los correos.
CREATE UNIQUE INDEX uk_screen_room_name_lower ON screen_room (lower(room_name));

-- uk_seat_room_position empieza por screen_room_id y sirve también para buscar butacas por sala.
CREATE TABLE seat
(
    uuid           UUID         NOT NULL,
    seat_row       VARCHAR(255) NOT NULL,
    seat_column    VARCHAR(255) NOT NULL,
    seat_type      VARCHAR(20)  NOT NULL,
    screen_room_id UUID         NOT NULL,
    CONSTRAINT pk_seat PRIMARY KEY (uuid),
    CONSTRAINT fk_seat_screen_room FOREIGN KEY (screen_room_id) REFERENCES screen_room (uuid) ON DELETE CASCADE,
    CONSTRAINT uk_seat_room_position UNIQUE (screen_room_id, seat_row, seat_column),
    CONSTRAINT ck_seat_type CHECK (seat_type IN ('NORMAL', 'PREMIUM'))
);

-- Funciones -----------------------------------------------------------------------------------------

CREATE TABLE movie_screening
(
    uuid           UUID           NOT NULL,
    movie_id       UUID           NOT NULL,
    screen_room_id UUID           NOT NULL,
    starts_at      TIMESTAMPTZ    NOT NULL,
    ends_at        TIMESTAMPTZ    NOT NULL,
    base_price     DECIMAL(10, 2) NOT NULL,
    archived_at    TIMESTAMPTZ,
    CONSTRAINT pk_movie_screening PRIMARY KEY (uuid),
    CONSTRAINT fk_movie_screening_movie FOREIGN KEY (movie_id) REFERENCES movie (uuid),
    CONSTRAINT fk_movie_screening_screen_room FOREIGN KEY (screen_room_id) REFERENCES screen_room (uuid),
    CONSTRAINT ck_movie_screening_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_movie_screening_base_price CHECK (base_price >= 0),
    -- Dos funciones vigentes de la misma sala no pueden superponerse; una archivada libera su
    -- horario. La comprobación previa de la aplicación sigue existiendo para poder nombrar la
    -- función con la que choca; esto cubre la carrera entre dos altas.
    CONSTRAINT ex_movie_screening_room_overlap
        EXCLUDE USING gist (screen_room_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
        WHERE (archived_at IS NULL)
);

CREATE INDEX idx_movie_screening_room_time ON movie_screening (screen_room_id, starts_at, ends_at);
CREATE INDEX idx_movie_screening_movie ON movie_screening (movie_id);

-- Confitería ----------------------------------------------------------------------------------------

-- El ajuste atómico ya impide el stock negativo, pero la venta y la edición escriben el valor
-- absoluto; ck_inventory_quantity es la última barrera.
CREATE TABLE inventory
(
    uuid         UUID           NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    category     VARCHAR(20)    NOT NULL,
    -- Precio de venta al público; cada línea de reserva lo copia como unit_price.
    cost         DECIMAL(10, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    version      BIGINT         NOT NULL DEFAULT 0,
    archived_at  TIMESTAMPTZ,
    CONSTRAINT pk_inventory PRIMARY KEY (uuid),
    CONSTRAINT ck_inventory_category CHECK (category IN ('SNACK', 'DRINK', 'COMBO', 'MERCHANDISE')),
    CONSTRAINT ck_inventory_cost CHECK (cost >= 0),
    CONSTRAINT ck_inventory_quantity CHECK (quantity >= 0)
);

CREATE UNIQUE INDEX uk_inventory_product_name_lower ON inventory (lower(product_name));

-- Auditoría de los movimientos manuales de stock: la carga inicial y cada ajuste quedan asentados con
-- su motivo y su autor (nulo si no lo hizo una persona). Las ventas y cancelaciones no se repiten acá
-- porque ya constan en reservation_item. Un producto con historial no se borra, se archiva: la clave
-- foránea no tiene ON DELETE CASCADE para que el historial no pueda perderse.
CREATE TABLE inventory_adjustment
(
    uuid            UUID         NOT NULL,
    inventory_id    UUID         NOT NULL,
    delta           INTEGER      NOT NULL,
    resulting_stock INTEGER      NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    adjusted_by     VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_inventory_adjustment PRIMARY KEY (uuid),
    CONSTRAINT fk_inventory_adjustment_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (uuid),
    -- Un ajuste de cero no describe ningún movimiento y sólo ensuciaría el historial.
    CONSTRAINT ck_inventory_adjustment_delta CHECK (delta <> 0),
    CONSTRAINT ck_inventory_adjustment_resulting_stock CHECK (resulting_stock >= 0)
);

-- El historial se consulta siempre por producto y en orden cronológico inverso.
CREATE INDEX idx_inventory_adjustment_inventory ON inventory_adjustment (inventory_id, created_at DESC);

-- Reservas ------------------------------------------------------------------------------------------

-- Cancelar es una transición de estado, no un borrado: entradas y líneas de confitería se conservan
-- y se marcan, para poder auditar qué se canceló. total_price pasa a ser lo adeudado y lo que se
-- dejó de cobrar queda en original_total_price.
CREATE TABLE reservation
(
    uuid                 UUID           NOT NULL,
    user_id              UUID           NOT NULL,
    movie_screening_id   UUID           NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    total_price          DECIMAL(10, 2) NOT NULL,
    original_total_price DECIMAL(10, 2),
    created_at           TIMESTAMPTZ    NOT NULL,
    cancelled_at         TIMESTAMPTZ,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_reservation PRIMARY KEY (uuid),
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES app_user (uuid),
    CONSTRAINT fk_reservation_movie_screening FOREIGN KEY (movie_screening_id) REFERENCES movie_screening (uuid),
    CONSTRAINT ck_reservation_total_price CHECK (total_price >= 0),
    CONSTRAINT ck_reservation_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_reservation_user ON reservation (user_id);
CREATE INDEX idx_reservation_screening ON reservation (movie_screening_id);
-- La vista de administración filtra y ordena por fecha de compra.
CREATE INDEX idx_reservation_created_at ON reservation (created_at);

CREATE TABLE ticket
(
    uuid               UUID           NOT NULL,
    reservation_id     UUID           NOT NULL,
    movie_screening_id UUID           NOT NULL,
    seat_id            UUID           NOT NULL,
    seat_type          VARCHAR(20)    NOT NULL,
    price              DECIMAL(10, 2) NOT NULL,
    cancelled_at       TIMESTAMPTZ,
    CONSTRAINT pk_ticket PRIMARY KEY (uuid),
    CONSTRAINT fk_ticket_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_movie_screening FOREIGN KEY (movie_screening_id) REFERENCES movie_screening (uuid),
    CONSTRAINT fk_ticket_seat FOREIGN KEY (seat_id) REFERENCES seat (uuid),
    CONSTRAINT ck_ticket_price CHECK (price >= 0),
    CONSTRAINT ck_ticket_seat_type CHECK (seat_type IN ('NORMAL', 'PREMIUM'))
);

-- La unicidad (función, butaca) no cuenta las entradas canceladas, o una butaca liberada no podría
-- volver a venderse. El índice único parcial expresa exactamente esa regla y es la barrera contra
-- la doble venta bajo concurrencia.
CREATE UNIQUE INDEX uk_ticket_screening_seat ON ticket (movie_screening_id, seat_id) WHERE cancelled_at IS NULL;
CREATE INDEX idx_ticket_reservation ON ticket (reservation_id);
-- «¿Esta butaca tiene entradas?» antes de editarla o borrarla. uk_ticket_screening_seat no sirve:
-- empieza por la función y excluye las canceladas.
CREATE INDEX idx_ticket_seat ON ticket (seat_id);

-- uk_reservation_item_product empieza por reservation_id y sirve también para buscar las líneas de
-- una reserva.
CREATE TABLE reservation_item
(
    uuid           UUID           NOT NULL,
    reservation_id UUID           NOT NULL,
    inventory_id   UUID           NOT NULL,
    product_name   VARCHAR(255)   NOT NULL,
    unit_price     DECIMAL(10, 2) NOT NULL,
    quantity       INTEGER        NOT NULL,
    line_total     DECIMAL(10, 2) NOT NULL,
    cancelled_at   TIMESTAMPTZ,
    CONSTRAINT pk_reservation_item PRIMARY KEY (uuid),
    CONSTRAINT fk_reservation_item_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_reservation_item_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (uuid),
    CONSTRAINT uk_reservation_item_product UNIQUE (reservation_id, inventory_id),
    CONSTRAINT ck_reservation_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_reservation_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_reservation_item_line_total CHECK (line_total >= 0)
);

CREATE INDEX idx_reservation_item_inventory ON reservation_item (inventory_id);

-- Bloqueos de butacas -------------------------------------------------------------------------------

-- Reserva temporal de butacas durante el proceso de compra, para que quien tarda eligiendo la
-- confitería no pierda la butaca y se entere recién con un 409. Tiene vida corta y se barre sola.
CREATE TABLE seat_hold
(
    uuid               UUID        NOT NULL,
    movie_screening_id UUID        NOT NULL,
    user_id            UUID        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    released_at        TIMESTAMPTZ,
    CONSTRAINT pk_seat_hold PRIMARY KEY (uuid),
    CONSTRAINT fk_seat_hold_movie_screening FOREIGN KEY (movie_screening_id) REFERENCES movie_screening (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_seat_hold_user FOREIGN KEY (user_id) REFERENCES app_user (uuid) ON DELETE CASCADE
);

CREATE TABLE seat_hold_seat
(
    seat_hold_id UUID NOT NULL,
    seat_id      UUID NOT NULL,
    CONSTRAINT pk_seat_hold_seat PRIMARY KEY (seat_hold_id, seat_id),
    CONSTRAINT fk_seat_hold_seat_hold FOREIGN KEY (seat_hold_id) REFERENCES seat_hold (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_seat_hold_seat_seat FOREIGN KEY (seat_id) REFERENCES seat (uuid) ON DELETE CASCADE
);

-- La comprobación caliente es «¿alguna de estas butacas está bloqueada y vigente?».
CREATE INDEX idx_seat_hold_screening_expires ON seat_hold (movie_screening_id, expires_at);
CREATE INDEX idx_seat_hold_seat_seat ON seat_hold_seat (seat_id);
-- El barrido busca los vencidos sin soltar de todas las funciones a la vez.
CREATE INDEX idx_seat_hold_expires_unreleased ON seat_hold (expires_at) WHERE released_at IS NULL;
CREATE INDEX idx_seat_hold_user ON seat_hold (user_id);

-- Tokens de refresco --------------------------------------------------------------------------------

-- Rotación con ventana de gracia: al refrescar, el token usado se marca como rotado y se sigue
-- aceptando un rato corto, para que dos pestañas refrescando a la vez o una respuesta perdida no
-- cierren la sesión. Pasada la ventana, volver a presentarlo es reutilización y se revoca toda la
-- familia.
CREATE TABLE refresh_token
(
    uuid       UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    family_id  UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    rotated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    -- Por qué se revocó: un cierre de sesión es normal; una reutilización es señal de robo.
    revoked_reason VARCHAR(20),
    CONSTRAINT pk_refresh_token PRIMARY KEY (uuid),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (uuid) ON DELETE CASCADE,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_revoked_reason
        CHECK (revoked_reason IN ('LOGOUT', 'REUSE', 'PASSWORD_CHANGED', 'ACCOUNT_DELETED')),
    CONSTRAINT ck_refresh_token_revocation CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_expires_at ON refresh_token (expires_at);
-- Revocar una familia entera es la operación de la detección de robo; sin índice recorrería la tabla.
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);

-- Mensajes de contacto ------------------------------------------------------------------------------

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
    CONSTRAINT fk_contact_message_user FOREIGN KEY (user_id) REFERENCES app_user (uuid) ON DELETE CASCADE,
    CONSTRAINT ck_contact_message_category
        CHECK (category IN ('GENERAL', 'RESERVATION', 'COMPLAINT', 'SUGGESTION'))
);

-- «Mis mensajes» filtra por autor; la bandeja de administración ordena por fecha.
CREATE INDEX idx_contact_message_user_created ON contact_message (user_id, created_at);
CREATE INDEX idx_contact_message_created ON contact_message (created_at);
