-- Reserva temporal de butacas durante el proceso de compra.
--
-- Sin esto, una butaca sólo queda tomada al confirmar: quien tarda eligiendo la confitería puede
-- perderla y se entera recién con un 409. El bloqueo tiene vida corta y se barre solo.
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
