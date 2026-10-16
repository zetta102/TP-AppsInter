-- Datos de demostración.
--
-- La base nacía vacía y el frontend no tenía nada que mostrar hasta cargar a mano películas, salas,
-- butacas y funciones. Esta semilla deja un catálogo chico pero realista en todas las tablas.
--
-- - Usuarios: sólo clientes, todos con la contraseña «Password123!». No se siembra administrador:
--   sigue saliendo de CINE_ADMIN_EMAIL/CINE_ADMIN_PASSWORD (AdminBootstrapRunner).
-- - Las funciones se ubican relativas al momento de la migración: desde tres días antes hasta trece
--   después, en UTC.
-- - Los importes siguen las mismas reglas que la aplicación: recargo premium de 1,50, fin de función
--   = inicio + duración + 15 minutos de limpieza, cancelación que deja total_price en cero.
-- - Las filas raíz usan UUID fijos y legibles (…0001xx usuarios, …0002xx películas, …0003xx salas,
--   …0004xx productos) para poder nombrarlas en la documentación.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Usuarios ------------------------------------------------------------------------------------------

INSERT INTO app_user (uuid, first_name, last_name, email, password, role, deleted_at)
VALUES ('00000000-0000-4000-8000-000000000101', 'Ana', 'García', 'ana.garcia@cine.example',
        crypt('Password123!', gen_salt('bf', 10)), 'CUSTOMER', NULL),
       ('00000000-0000-4000-8000-000000000102', 'Bruno', 'Fernández', 'bruno.fernandez@cine.example',
        crypt('Password123!', gen_salt('bf', 10)), 'CUSTOMER', NULL),
       ('00000000-0000-4000-8000-000000000103', 'Carla', 'López', 'carla.lopez@cine.example',
        crypt('Password123!', gen_salt('bf', 10)), 'CUSTOMER', NULL),
       ('00000000-0000-4000-8000-000000000104', 'Diego', 'Martínez', 'diego.martinez@cine.example',
        crypt('Password123!', gen_salt('bf', 10)), 'CUSTOMER', NULL),
       ('00000000-0000-4000-8000-000000000105', 'Elena', 'Sosa', 'elena.sosa@cine.example',
        crypt('Password123!', gen_salt('bf', 10)), 'CUSTOMER', NULL),
       -- Dado de baja: conserva su historial pero no puede iniciar sesión.
       ('00000000-0000-4000-8000-000000000106', 'Federico', 'Romero', 'federico.romero@cine.example',
        crypt('Password123!', gen_salt('bf', 10)), 'CUSTOMER', now() - INTERVAL '20 days');

-- Películas -----------------------------------------------------------------------------------------

INSERT INTO movie (uuid, movie_name, runtime, description, director, release_year, certification,
                   original_language, archived_at)
VALUES ('00000000-0000-4000-8000-000000000201', 'The Matrix', 136,
        'Un hacker descubre que el mundo en el que vive es una simulación y se une a la rebelión contra sus máquinas.',
        'Lana Wachowski, Lilly Wachowski', 1999, 'R', 'en', NULL),
       ('00000000-0000-4000-8000-000000000202', 'Spirited Away', 125,
        'Una niña se adentra en un mundo de espíritus y debe trabajar en una casa de baños para liberar a sus padres.',
        'Hayao Miyazaki', 2001, 'PG', 'ja', NULL),
       ('00000000-0000-4000-8000-000000000203', 'Pulp Fiction', 154,
        'Las vidas de dos sicarios, un boxeador y la esposa de un gánster se entrecruzan en cuatro historias de violencia y redención.',
        'Quentin Tarantino', 1994, 'R', 'en', NULL),
       ('00000000-0000-4000-8000-000000000204', 'Amélie', 122,
        'Una tímida camarera de Montmartre decide cambiar en secreto la vida de quienes la rodean.',
        'Jean-Pierre Jeunet', 2001, 'R', 'fr', NULL),
       ('00000000-0000-4000-8000-000000000205', 'El laberinto del fauno', 118,
        'En la España de 1944, una niña huye de su cruel padrastro hacia un mundo fantástico, misterioso y peligroso.',
        'Guillermo del Toro', 2006, 'R', 'es', NULL),
       ('00000000-0000-4000-8000-000000000206', 'Inception', 148,
        'A un ladrón que roba secretos a través de los sueños le ofrecen, en cambio, implantar una idea.',
        'Christopher Nolan', 2010, 'PG-13', 'en', NULL),
       ('00000000-0000-4000-8000-000000000207', 'Toy Story', 81,
        'Un muñeco vaquero se siente amenazado cuando un nuevo juguete, un guardián espacial, se convierte en el favorito de su dueño.',
        'John Lasseter', 1995, 'G', 'en', NULL),
       ('00000000-0000-4000-8000-000000000208', 'The Shining', 146,
        'Un escritor acepta cuidar durante el invierno un hotel aislado, donde lo espera una presencia siniestra.',
        'Stanley Kubrick', 1980, 'R', 'en', NULL),
       ('00000000-0000-4000-8000-000000000209', 'Parasite', 132,
        'Una familia pobre se las ingenia para conseguir trabajo en la casa de una familia adinerada, con resultados inesperados.',
        'Bong Joon-ho', 2019, 'R', 'ko', NULL),
       ('00000000-0000-4000-8000-000000000210', 'Mad Max: Fury Road', 120,
        'En un páramo postapocalíptico, un vagabundo y una guerrera rebelde huyen de un tirano a través del desierto.',
        'George Miller', 2015, 'R', 'en', NULL),
       ('00000000-0000-4000-8000-000000000211', 'La La Land', 128,
        'Un pianista de jazz y una aspirante a actriz se enamoran mientras persiguen sus sueños en Los Ángeles.',
        'Damien Chazelle', 2016, 'PG-13', 'en', NULL),
       -- Archivada: no tiene funciones y sólo aparece con includeArchived=true.
       ('00000000-0000-4000-8000-000000000212', 'El secreto de sus ojos', 129,
        'Un investigador judicial retirado escribe una novela sobre un asesinato sin resolver que todavía lo atormenta.',
        'Juan José Campanella', 2009, 'R', 'es', now() - INTERVAL '10 days');

INSERT INTO movie_genre (movie_id, genre)
VALUES ('00000000-0000-4000-8000-000000000201', 'ACTION'),
       ('00000000-0000-4000-8000-000000000201', 'SCI_FI'),
       ('00000000-0000-4000-8000-000000000202', 'ANIMATION'),
       ('00000000-0000-4000-8000-000000000203', 'THRILLER'),
       ('00000000-0000-4000-8000-000000000203', 'DRAMA'),
       ('00000000-0000-4000-8000-000000000204', 'ROMANCE'),
       ('00000000-0000-4000-8000-000000000204', 'COMEDY'),
       ('00000000-0000-4000-8000-000000000205', 'DRAMA'),
       ('00000000-0000-4000-8000-000000000206', 'ACTION'),
       ('00000000-0000-4000-8000-000000000206', 'SCI_FI'),
       ('00000000-0000-4000-8000-000000000206', 'THRILLER'),
       ('00000000-0000-4000-8000-000000000207', 'ANIMATION'),
       ('00000000-0000-4000-8000-000000000207', 'COMEDY'),
       ('00000000-0000-4000-8000-000000000208', 'TERROR'),
       ('00000000-0000-4000-8000-000000000209', 'THRILLER'),
       ('00000000-0000-4000-8000-000000000209', 'DRAMA'),
       ('00000000-0000-4000-8000-000000000210', 'ACTION'),
       ('00000000-0000-4000-8000-000000000211', 'ROMANCE'),
       ('00000000-0000-4000-8000-000000000211', 'DRAMA'),
       ('00000000-0000-4000-8000-000000000212', 'THRILLER'),
       ('00000000-0000-4000-8000-000000000212', 'DRAMA');

INSERT INTO movie_main_cast (movie_id, actor)
VALUES ('00000000-0000-4000-8000-000000000201', 'Keanu Reeves'),
       ('00000000-0000-4000-8000-000000000201', 'Laurence Fishburne'),
       ('00000000-0000-4000-8000-000000000201', 'Carrie-Anne Moss'),
       ('00000000-0000-4000-8000-000000000202', 'Rumi Hiiragi'),
       ('00000000-0000-4000-8000-000000000202', 'Miyu Irino'),
       ('00000000-0000-4000-8000-000000000203', 'John Travolta'),
       ('00000000-0000-4000-8000-000000000203', 'Samuel L. Jackson'),
       ('00000000-0000-4000-8000-000000000203', 'Uma Thurman'),
       ('00000000-0000-4000-8000-000000000203', 'Bruce Willis'),
       ('00000000-0000-4000-8000-000000000204', 'Audrey Tautou'),
       ('00000000-0000-4000-8000-000000000204', 'Mathieu Kassovitz'),
       ('00000000-0000-4000-8000-000000000205', 'Ivana Baquero'),
       ('00000000-0000-4000-8000-000000000205', 'Sergi López'),
       ('00000000-0000-4000-8000-000000000205', 'Maribel Verdú'),
       ('00000000-0000-4000-8000-000000000206', 'Leonardo DiCaprio'),
       ('00000000-0000-4000-8000-000000000206', 'Joseph Gordon-Levitt'),
       ('00000000-0000-4000-8000-000000000206', 'Elliot Page'),
       ('00000000-0000-4000-8000-000000000207', 'Tom Hanks'),
       ('00000000-0000-4000-8000-000000000207', 'Tim Allen'),
       ('00000000-0000-4000-8000-000000000208', 'Jack Nicholson'),
       ('00000000-0000-4000-8000-000000000208', 'Shelley Duvall'),
       ('00000000-0000-4000-8000-000000000209', 'Song Kang-ho'),
       ('00000000-0000-4000-8000-000000000209', 'Choi Woo-shik'),
       ('00000000-0000-4000-8000-000000000209', 'Park So-dam'),
       ('00000000-0000-4000-8000-000000000210', 'Tom Hardy'),
       ('00000000-0000-4000-8000-000000000210', 'Charlize Theron'),
       ('00000000-0000-4000-8000-000000000210', 'Nicholas Hoult'),
       ('00000000-0000-4000-8000-000000000211', 'Ryan Gosling'),
       ('00000000-0000-4000-8000-000000000211', 'Emma Stone'),
       ('00000000-0000-4000-8000-000000000212', 'Ricardo Darín'),
       ('00000000-0000-4000-8000-000000000212', 'Soledad Villamil'),
       ('00000000-0000-4000-8000-000000000212', 'Guillermo Francella');

-- Salas y butacas -----------------------------------------------------------------------------------

INSERT INTO screen_room (uuid, room_name, screen_room_type)
VALUES ('00000000-0000-4000-8000-000000000301', 'Sala 1', 'NORMAL'),
       ('00000000-0000-4000-8000-000000000302', 'Sala 2', 'THREED'),
       ('00000000-0000-4000-8000-000000000303', 'Sala 3', 'FOURD'),
       ('00000000-0000-4000-8000-000000000304', 'Sala IMAX', 'IMAX');

-- Filas A… y columnas 1…; las dos últimas filas de cada sala son premium.
INSERT INTO seat (uuid, seat_row, seat_column, seat_type, screen_room_id)
SELECT gen_random_uuid(),
       chr(64 + r),
       c::text,
       CASE WHEN r > layout.row_count - 2 THEN 'PREMIUM' ELSE 'NORMAL' END,
       layout.room_id
FROM (VALUES ('00000000-0000-4000-8000-000000000301'::uuid, 8, 12),
             ('00000000-0000-4000-8000-000000000302'::uuid, 8, 10),
             ('00000000-0000-4000-8000-000000000303'::uuid, 7, 10),
             ('00000000-0000-4000-8000-000000000304'::uuid, 12, 12)) AS layout (room_id, row_count, column_count)
         CROSS JOIN LATERAL generate_series(1, layout.row_count) AS r
         CROSS JOIN LATERAL generate_series(1, layout.column_count) AS c;

-- Funciones -----------------------------------------------------------------------------------------

-- Cuatro turnos diarios por sala, separados 195 minutos: alcanza para la película más larga del
-- catálogo (154) más los 15 de limpieza. Las películas rotan por sala, día y turno.
CREATE TEMPORARY TABLE seed_slot AS
SELECT d AS day_offset,
       s AS slot,
       date_trunc('day', now()) + d * INTERVAL '1 day' + INTERVAL '13 hours' + s * INTERVAL '195 minutes' AS starts_at
FROM generate_series(-3, 13) AS d
         CROSS JOIN generate_series(0, 3) AS s;

INSERT INTO movie_screening (uuid, movie_id, screen_room_id, starts_at, ends_at, base_price)
SELECT gen_random_uuid(),
       m.uuid,
       room.room_id,
       slot.starts_at,
       slot.starts_at + (m.runtime + 15) * INTERVAL '1 minute',
       room.base_price
FROM seed_slot slot
         CROSS JOIN (VALUES ('00000000-0000-4000-8000-000000000301'::uuid, 0, 8.00),
                            ('00000000-0000-4000-8000-000000000302'::uuid, 1, 10.00),
                            ('00000000-0000-4000-8000-000000000303'::uuid, 2, 12.00),
                            ('00000000-0000-4000-8000-000000000304'::uuid, 3, 15.00)) AS room (room_id, idx, base_price)
         JOIN (SELECT uuid, runtime, row_number() OVER (ORDER BY uuid) - 1 AS idx
               FROM movie
               WHERE archived_at IS NULL) AS m
              ON m.idx = (slot.day_offset + 3 + room.idx * 3 + slot.slot * 5) % 11;

-- Reservas y entradas -------------------------------------------------------------------------------

CREATE TEMPORARY TABLE seed_reservation
(
    n          INTEGER PRIMARY KEY,
    uuid       UUID    NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID    NOT NULL,
    day_offset INTEGER NOT NULL,
    room_id    UUID    NOT NULL,
    slot       INTEGER NOT NULL,
    seat_row   TEXT    NOT NULL,
    first_col  INTEGER NOT NULL,
    seat_count INTEGER NOT NULL,
    cancelled  BOOLEAN NOT NULL
);

INSERT INTO seed_reservation (n, user_id, day_offset, room_id, slot, seat_row, first_col, seat_count, cancelled)
VALUES (1, '00000000-0000-4000-8000-000000000101', -3, '00000000-0000-4000-8000-000000000301', 2, 'D', 5, 2, FALSE),
       (2, '00000000-0000-4000-8000-000000000102', -3, '00000000-0000-4000-8000-000000000301', 2, 'H', 1, 2, FALSE),
       (3, '00000000-0000-4000-8000-000000000103', -2, '00000000-0000-4000-8000-000000000304', 3, 'F', 3, 3, FALSE),
       (4, '00000000-0000-4000-8000-000000000104', -2, '00000000-0000-4000-8000-000000000302', 1, 'C', 4, 2, TRUE),
       (5, '00000000-0000-4000-8000-000000000105', -1, '00000000-0000-4000-8000-000000000303', 2, 'G', 2, 4, FALSE),
       (6, '00000000-0000-4000-8000-000000000106', -1, '00000000-0000-4000-8000-000000000301', 3, 'E', 6, 2, FALSE),
       (7, '00000000-0000-4000-8000-000000000101', -1, '00000000-0000-4000-8000-000000000304', 2, 'K', 5, 2, FALSE),
       (8, '00000000-0000-4000-8000-000000000101', 1, '00000000-0000-4000-8000-000000000301', 2, 'E', 5, 2, FALSE),
       (9, '00000000-0000-4000-8000-000000000102', 1, '00000000-0000-4000-8000-000000000301', 2, 'E', 7, 3, FALSE),
       (10, '00000000-0000-4000-8000-000000000103', 1, '00000000-0000-4000-8000-000000000304', 3, 'L', 4, 4, FALSE),
       (11, '00000000-0000-4000-8000-000000000104', 2, '00000000-0000-4000-8000-000000000302', 2, 'B', 3, 1, FALSE),
       (12, '00000000-0000-4000-8000-000000000105', 2, '00000000-0000-4000-8000-000000000303', 3, 'D', 4, 2, TRUE),
       (13, '00000000-0000-4000-8000-000000000101', 3, '00000000-0000-4000-8000-000000000304', 0, 'G', 6, 2, FALSE),
       (14, '00000000-0000-4000-8000-000000000102', 3, '00000000-0000-4000-8000-000000000302', 3, 'H', 2, 2, FALSE),
       (15, '00000000-0000-4000-8000-000000000103', 4, '00000000-0000-4000-8000-000000000301', 1, 'C', 1, 3, FALSE),
       (16, '00000000-0000-4000-8000-000000000104', 5, '00000000-0000-4000-8000-000000000304', 2, 'J', 6, 2, TRUE),
       (17, '00000000-0000-4000-8000-000000000105', 5, '00000000-0000-4000-8000-000000000303', 0, 'A', 5, 1, FALSE),
       (18, '00000000-0000-4000-8000-000000000101', 6, '00000000-0000-4000-8000-000000000301', 3, 'F', 8, 4, FALSE),
       (19, '00000000-0000-4000-8000-000000000102', 8, '00000000-0000-4000-8000-000000000302', 2, 'D', 4, 2, FALSE),
       (20, '00000000-0000-4000-8000-000000000103', 10, '00000000-0000-4000-8000-000000000304', 3, 'H', 1, 3, FALSE);

-- El total se calcula más abajo, una vez cargadas entradas y confitería.
INSERT INTO reservation (uuid, user_id, movie_screening_id, status, total_price, created_at, version)
SELECT sr.uuid,
       sr.user_id,
       ms.uuid,
       'CONFIRMED',
       0,
       least(now() - sr.n * INTERVAL '1 hour', ms.starts_at - INTERVAL '3 hours'),
       0
FROM seed_reservation sr
         JOIN seed_slot slot ON slot.day_offset = sr.day_offset AND slot.slot = sr.slot
         JOIN movie_screening ms ON ms.screen_room_id = sr.room_id AND ms.starts_at = slot.starts_at;

INSERT INTO ticket (uuid, reservation_id, movie_screening_id, seat_id, seat_type, price)
SELECT gen_random_uuid(),
       r.uuid,
       r.movie_screening_id,
       seat.uuid,
       seat.seat_type,
       round(ms.base_price * CASE WHEN seat.seat_type = 'PREMIUM' THEN 1.50 ELSE 1 END, 2)
FROM seed_reservation sr
         JOIN reservation r ON r.uuid = sr.uuid
         JOIN movie_screening ms ON ms.uuid = r.movie_screening_id
         CROSS JOIN LATERAL generate_series(sr.first_col, sr.first_col + sr.seat_count - 1) AS c
         JOIN seat ON seat.screen_room_id = sr.room_id
    AND seat.seat_row = sr.seat_row
    AND seat.seat_column = c::text;

-- Confitería ----------------------------------------------------------------------------------------

CREATE TEMPORARY TABLE seed_product
(
    uuid          UUID           NOT NULL,
    product_name  TEXT           NOT NULL,
    category      TEXT           NOT NULL,
    cost          DECIMAL(10, 2) NOT NULL,
    initial_stock INTEGER        NOT NULL,
    archived      BOOLEAN        NOT NULL
);

INSERT INTO seed_product (uuid, product_name, category, cost, initial_stock, archived)
VALUES ('00000000-0000-4000-8000-000000000401', 'Pochoclos chicos', 'SNACK', 4.50, 200, FALSE),
       ('00000000-0000-4000-8000-000000000402', 'Pochoclos grandes', 'SNACK', 6.50, 200, FALSE),
       ('00000000-0000-4000-8000-000000000403', 'Nachos con queso', 'SNACK', 7.00, 80, FALSE),
       ('00000000-0000-4000-8000-000000000404', 'Barra de chocolate', 'SNACK', 2.50, 150, FALSE),
       ('00000000-0000-4000-8000-000000000405', 'Gaseosa chica', 'DRINK', 3.00, 250, FALSE),
       ('00000000-0000-4000-8000-000000000406', 'Gaseosa grande', 'DRINK', 4.50, 250, FALSE),
       ('00000000-0000-4000-8000-000000000407', 'Agua mineral', 'DRINK', 2.00, 120, FALSE),
       ('00000000-0000-4000-8000-000000000408', 'Café', 'DRINK', 3.50, 60, FALSE),
       ('00000000-0000-4000-8000-000000000409', 'Combo individual', 'COMBO', 9.50, 100, FALSE),
       ('00000000-0000-4000-8000-000000000410', 'Combo pareja', 'COMBO', 16.00, 100, FALSE),
       ('00000000-0000-4000-8000-000000000411', 'Combo familiar', 'COMBO', 24.00, 50, FALSE),
       ('00000000-0000-4000-8000-000000000412', 'Vaso coleccionable', 'MERCHANDISE', 12.00, 40, FALSE),
       ('00000000-0000-4000-8000-000000000413', 'Póster oficial', 'MERCHANDISE', 15.00, 25, FALSE),
       ('00000000-0000-4000-8000-000000000414', 'Llavero', 'MERCHANDISE', 5.00, 60, FALSE),
       -- Archivado: fuera de la góndola, pero visible con includeArchived=true.
       ('00000000-0000-4000-8000-000000000415', 'Combo de temporada', 'COMBO', 11.00, 10, TRUE);

INSERT INTO inventory (uuid, product_name, category, cost, quantity, version, archived_at)
SELECT uuid,
       product_name,
       category,
       cost,
       initial_stock,
       0,
       CASE WHEN archived THEN now() - INTERVAL '5 days' END
FROM seed_product;

-- Cada producto arranca con su carga inicial; algunos tienen además un movimiento posterior.
INSERT INTO inventory_adjustment (uuid, inventory_id, delta, resulting_stock, reason, adjusted_by, created_at)
SELECT gen_random_uuid(), uuid, initial_stock, initial_stock, 'Stock inicial', 'sistema', now() - INTERVAL '30 days'
FROM seed_product;

CREATE TEMPORARY TABLE seed_adjustment
(
    inventory_id UUID    NOT NULL,
    delta        INTEGER NOT NULL,
    reason       TEXT    NOT NULL,
    days_ago     INTEGER NOT NULL
);

INSERT INTO seed_adjustment (inventory_id, delta, reason, days_ago)
VALUES ('00000000-0000-4000-8000-000000000403', -5, 'Mercadería vencida', 12),
       ('00000000-0000-4000-8000-000000000405', 100, 'Reposición de proveedor', 8),
       ('00000000-0000-4000-8000-000000000408', 30, 'Reposición de proveedor', 6),
       ('00000000-0000-4000-8000-000000000412', -2, 'Rotura en depósito', 4);

INSERT INTO inventory_adjustment (uuid, inventory_id, delta, resulting_stock, reason, adjusted_by, created_at)
SELECT gen_random_uuid(),
       a.inventory_id,
       a.delta,
       i.quantity + a.delta,
       a.reason,
       'sistema',
       now() - a.days_ago * INTERVAL '1 day'
FROM seed_adjustment a
         JOIN inventory i ON i.uuid = a.inventory_id;

UPDATE inventory i
SET quantity = i.quantity + a.delta
FROM seed_adjustment a
WHERE a.inventory_id = i.uuid;

CREATE TEMPORARY TABLE seed_item
(
    n            INTEGER NOT NULL,
    inventory_id UUID    NOT NULL,
    quantity     INTEGER NOT NULL
);

INSERT INTO seed_item (n, inventory_id, quantity)
VALUES (1, '00000000-0000-4000-8000-000000000410', 1),
       (3, '00000000-0000-4000-8000-000000000409', 1),
       (3, '00000000-0000-4000-8000-000000000406', 2),
       (5, '00000000-0000-4000-8000-000000000411', 1),
       (7, '00000000-0000-4000-8000-000000000402', 2),
       (7, '00000000-0000-4000-8000-000000000405', 2),
       (8, '00000000-0000-4000-8000-000000000410', 1),
       (10, '00000000-0000-4000-8000-000000000411', 1),
       (10, '00000000-0000-4000-8000-000000000412', 1),
       (12, '00000000-0000-4000-8000-000000000409', 2),
       (13, '00000000-0000-4000-8000-000000000403', 1),
       (13, '00000000-0000-4000-8000-000000000406', 1),
       (15, '00000000-0000-4000-8000-000000000401', 3),
       (18, '00000000-0000-4000-8000-000000000410', 2),
       (20, '00000000-0000-4000-8000-000000000413', 1),
       (20, '00000000-0000-4000-8000-000000000404', 2);

INSERT INTO reservation_item (uuid, reservation_id, inventory_id, product_name, unit_price, quantity, line_total)
SELECT gen_random_uuid(),
       sr.uuid,
       i.uuid,
       i.product_name,
       i.cost,
       si.quantity,
       i.cost * si.quantity
FROM seed_item si
         JOIN seed_reservation sr ON sr.n = si.n
         JOIN inventory i ON i.uuid = si.inventory_id;

-- Lo vendido sale de la góndola; lo de las reservas canceladas ya volvió.
UPDATE inventory i
SET quantity = i.quantity - sold.units
FROM (SELECT si.inventory_id, sum(si.quantity) AS units
      FROM seed_item si
               JOIN seed_reservation sr ON sr.n = si.n
      WHERE NOT sr.cancelled
      GROUP BY si.inventory_id) AS sold
WHERE sold.inventory_id = i.uuid;

-- Totales y cancelaciones ---------------------------------------------------------------------------

UPDATE reservation r
SET total_price = (SELECT coalesce(sum(t.price), 0) FROM ticket t WHERE t.reservation_id = r.uuid)
                      + (SELECT coalesce(sum(ri.line_total), 0) FROM reservation_item ri WHERE ri.reservation_id = r.uuid)
FROM seed_reservation sr
WHERE sr.uuid = r.uuid;

-- Igual que ReservationService.cancelReservation: el total pasa a ser lo adeudado (cero) y lo que se
-- dejó de cobrar queda en original_total_price.
UPDATE reservation r
SET status               = 'CANCELLED',
    cancelled_at         = least(now(), r.created_at + INTERVAL '1 hour'),
    original_total_price = r.total_price,
    total_price          = 0
FROM seed_reservation sr
WHERE sr.uuid = r.uuid
  AND sr.cancelled;

UPDATE ticket t
SET cancelled_at = r.cancelled_at
FROM reservation r
WHERE r.uuid = t.reservation_id
  AND r.status = 'CANCELLED';

-- Tokens de refresco --------------------------------------------------------------------------------

-- Historial inutilizable: sólo se guarda el hash de un valor aleatorio que nadie conoce. Uno vencido,
-- uno revocado y una familia rotada cuyo sucesor se cerró al salir.
INSERT INTO refresh_token (uuid, user_id, token_hash, expires_at, revoked_at, rotated_at, family_id, created_at)
VALUES ('00000000-0000-4000-8000-000000000501', '00000000-0000-4000-8000-000000000101',
        encode(sha256(gen_random_bytes(32)), 'hex'),
        now() - INTERVAL '10 days', NULL, NULL,
        '00000000-0000-4000-8000-000000000501', now() - INTERVAL '40 days'),
       ('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000102',
        encode(sha256(gen_random_bytes(32)), 'hex'),
        now() + INTERVAL '25 days', now() - INTERVAL '4 days', NULL,
        '00000000-0000-4000-8000-000000000502', now() - INTERVAL '5 days'),
       ('00000000-0000-4000-8000-000000000503', '00000000-0000-4000-8000-000000000103',
        encode(sha256(gen_random_bytes(32)), 'hex'),
        now() + INTERVAL '27 days', NULL, now() - INTERVAL '2 days',
        '00000000-0000-4000-8000-000000000503', now() - INTERVAL '3 days'),
       ('00000000-0000-4000-8000-000000000504', '00000000-0000-4000-8000-000000000103',
        encode(sha256(gen_random_bytes(32)), 'hex'),
        now() + INTERVAL '28 days', now() - INTERVAL '1 day', NULL,
        '00000000-0000-4000-8000-000000000503', now() - INTERVAL '2 days');

-- Bloqueos de butacas -------------------------------------------------------------------------------

-- Uno ya liberado y otro vigente por cinco minutos desde la migración, que el barrido limpia solo.
-- Ninguno pisa butacas vendidas.
CREATE TEMPORARY TABLE seed_hold
(
    uuid       UUID    NOT NULL,
    user_id    UUID    NOT NULL,
    day_offset INTEGER NOT NULL,
    room_id    UUID    NOT NULL,
    slot       INTEGER NOT NULL,
    seat_row   TEXT    NOT NULL,
    columns    TEXT[]  NOT NULL,
    released   BOOLEAN NOT NULL
);

INSERT INTO seed_hold (uuid, user_id, day_offset, room_id, slot, seat_row, columns, released)
VALUES ('00000000-0000-4000-8000-000000000601', '00000000-0000-4000-8000-000000000102', 2,
        '00000000-0000-4000-8000-000000000301', 1, 'A', ARRAY ['1', '2'], TRUE),
       ('00000000-0000-4000-8000-000000000602', '00000000-0000-4000-8000-000000000103', 3,
        '00000000-0000-4000-8000-000000000304', 2, 'F', ARRAY ['5', '6'], FALSE);

INSERT INTO seat_hold (uuid, movie_screening_id, user_id, created_at, expires_at, released_at)
SELECT h.uuid,
       ms.uuid,
       h.user_id,
       CASE WHEN h.released THEN now() - INTERVAL '1 hour' ELSE now() END,
       CASE WHEN h.released THEN now() - INTERVAL '55 minutes' ELSE now() + INTERVAL '5 minutes' END,
       CASE WHEN h.released THEN now() - INTERVAL '56 minutes' END
FROM seed_hold h
         JOIN seed_slot slot ON slot.day_offset = h.day_offset AND slot.slot = h.slot
         JOIN movie_screening ms ON ms.screen_room_id = h.room_id AND ms.starts_at = slot.starts_at;

INSERT INTO seat_hold_seat (seat_hold_id, seat_id)
SELECT h.uuid, seat.uuid
FROM seed_hold h
         CROSS JOIN LATERAL unnest(h.columns) AS c
         JOIN seat ON seat.screen_room_id = h.room_id
    AND seat.seat_row = h.seat_row
    AND seat.seat_column = c;

DROP TABLE seed_slot, seed_reservation, seed_product, seed_adjustment, seed_item, seed_hold;
