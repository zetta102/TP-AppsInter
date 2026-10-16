-- Archivado en lugar de borrado (frontend B5).
--
-- Lo que tiene historia —entradas, aunque estén canceladas— no puede borrarse, pero sí dejar de
-- ofrecerse. Un recurso archivado desaparece de los listados por omisión y no admite operaciones
-- nuevas, pero sigue resolviéndose por identificador para que las reservas viejas no se rompan.

ALTER TABLE movie ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE screen_room ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE movie_screening ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE inventory ADD COLUMN archived_at TIMESTAMPTZ;

-- Una función archivada libera su horario: otra puede ocupar la sala en esa franja.
ALTER TABLE movie_screening DROP CONSTRAINT ex_movie_screening_room_overlap;
ALTER TABLE movie_screening
    ADD CONSTRAINT ex_movie_screening_room_overlap
        EXCLUDE USING gist (screen_room_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
        WHERE (archived_at IS NULL);
