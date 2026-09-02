CREATE TABLE app_user
(
    uuid       UUID         NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (uuid),
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE TABLE movie
(
    uuid        UUID         NOT NULL,
    movie_name  VARCHAR(255) NOT NULL,
    runtime     INTEGER      NOT NULL,
    description VARCHAR(2000),
    director    VARCHAR(255),
    CONSTRAINT pk_movie PRIMARY KEY (uuid)
);

CREATE TABLE movie_genre
(
    movie_id UUID        NOT NULL,
    genre    VARCHAR(30) NOT NULL,
    CONSTRAINT fk_movie_genre_movie FOREIGN KEY (movie_id) REFERENCES movie (uuid) ON DELETE CASCADE
);

CREATE INDEX idx_movie_genre_movie ON movie_genre (movie_id);

CREATE TABLE movie_main_cast
(
    movie_id UUID         NOT NULL,
    actor    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_movie_main_cast_movie FOREIGN KEY (movie_id) REFERENCES movie (uuid) ON DELETE CASCADE
);

CREATE INDEX idx_movie_main_cast_movie ON movie_main_cast (movie_id);

CREATE TABLE screen_room
(
    uuid             UUID         NOT NULL,
    room_name        VARCHAR(255) NOT NULL,
    screen_room_type VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_screen_room PRIMARY KEY (uuid),
    CONSTRAINT uk_screen_room_name UNIQUE (room_name)
);

CREATE TABLE seat
(
    uuid           UUID         NOT NULL,
    seat_row       VARCHAR(255) NOT NULL,
    seat_column    VARCHAR(255) NOT NULL,
    seat_type      VARCHAR(20)  NOT NULL,
    screen_room_id UUID         NOT NULL,
    CONSTRAINT pk_seat PRIMARY KEY (uuid),
    CONSTRAINT fk_seat_screen_room FOREIGN KEY (screen_room_id) REFERENCES screen_room (uuid) ON DELETE CASCADE,
    CONSTRAINT uk_seat_room_position UNIQUE (screen_room_id, seat_row, seat_column)
);

CREATE INDEX idx_seat_screen_room ON seat (screen_room_id);

CREATE TABLE movie_screening
(
    uuid           UUID           NOT NULL,
    movie_id       UUID           NOT NULL,
    screen_room_id UUID           NOT NULL,
    starts_at      TIMESTAMP      NOT NULL,
    ends_at        TIMESTAMP      NOT NULL,
    base_price     DECIMAL(10, 2) NOT NULL,
    CONSTRAINT pk_movie_screening PRIMARY KEY (uuid),
    CONSTRAINT fk_movie_screening_movie FOREIGN KEY (movie_id) REFERENCES movie (uuid),
    CONSTRAINT fk_movie_screening_screen_room FOREIGN KEY (screen_room_id) REFERENCES screen_room (uuid)
);

CREATE INDEX idx_movie_screening_room_time ON movie_screening (screen_room_id, starts_at, ends_at);
CREATE INDEX idx_movie_screening_movie ON movie_screening (movie_id);

CREATE TABLE reservation
(
    uuid               UUID           NOT NULL,
    user_id            UUID           NOT NULL,
    movie_screening_id UUID           NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    total_price        DECIMAL(10, 2) NOT NULL,
    created_at         TIMESTAMP      NOT NULL,
    version            BIGINT,
    CONSTRAINT pk_reservation PRIMARY KEY (uuid),
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES app_user (uuid),
    CONSTRAINT fk_reservation_movie_screening FOREIGN KEY (movie_screening_id) REFERENCES movie_screening (uuid)
);

CREATE INDEX idx_reservation_user ON reservation (user_id);
CREATE INDEX idx_reservation_screening ON reservation (movie_screening_id);

CREATE TABLE ticket
(
    uuid               UUID           NOT NULL,
    reservation_id     UUID           NOT NULL,
    movie_screening_id UUID           NOT NULL,
    seat_id            UUID           NOT NULL,
    seat_type          VARCHAR(20)    NOT NULL,
    price              DECIMAL(10, 2) NOT NULL,
    CONSTRAINT pk_ticket PRIMARY KEY (uuid),
    CONSTRAINT fk_ticket_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_movie_screening FOREIGN KEY (movie_screening_id) REFERENCES movie_screening (uuid),
    CONSTRAINT fk_ticket_seat FOREIGN KEY (seat_id) REFERENCES seat (uuid),
    CONSTRAINT uk_ticket_screening_seat UNIQUE (movie_screening_id, seat_id)
);

CREATE INDEX idx_ticket_reservation ON ticket (reservation_id);

CREATE TABLE inventory
(
    uuid         UUID           NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    category     VARCHAR(20)    NOT NULL,
    cost         DECIMAL(10, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    CONSTRAINT pk_inventory PRIMARY KEY (uuid),
    CONSTRAINT uk_inventory_product_name UNIQUE (product_name)
);
