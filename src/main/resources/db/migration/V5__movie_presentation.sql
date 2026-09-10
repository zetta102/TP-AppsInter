-- Campos de presentación del catálogo. Todos opcionales: las películas ya cargadas quedan en NULL
-- y el frontend sigue mostrando su marcador de posición hasta que alguien complete los datos.
ALTER TABLE movie
    ADD COLUMN poster_url        VARCHAR(2048),
    ADD COLUMN backdrop_url      VARCHAR(2048),
    ADD COLUMN release_year      INTEGER,
    ADD COLUMN certification     VARCHAR(16),
    ADD COLUMN original_language VARCHAR(8),
    ADD COLUMN trailer_url       VARCHAR(2048);

-- El filtro por año recorre el catálogo entero en la pantalla de búsqueda.
CREATE INDEX idx_movie_release_year ON movie (release_year);
