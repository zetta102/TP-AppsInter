package tp.appsinter.cine.movie;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface MovieRepository extends JpaRepository<Movie, UUID> {

    /**
     * Búsqueda del catálogo. Los géneros se combinan con «o»: la película entra si tiene alguno de
     * los pedidos, que es lo que espera un selector múltiple.
     *
     * @param pattern         patrón «contiene» ya escapado ({@code CriteriaSupport.containsPattern}),
     *                        o {@code null} para no filtrar por nombre
     * @param genres          {@code null} para no filtrar por género; una lista vacía no se admite
     *                        porque no dejaría pasar nada
     * @param includeArchived si es falso, las películas archivadas no aparecen
     */
    @Query("""
            select distinct m
            from Movie m
            left join m.genres g
            where (:pattern is null or lower(m.movieName) like cast(:pattern as String) escape '\\')
              and (:genres is null or g in :genres)
              and (:releaseYear is null or m.releaseYear = :releaseYear)
              and (:includeArchived = true or m.archivedAt is null)
            """)
    Page<Movie> search(@Param("pattern") @Nullable String pattern,
                       @Param("genres") @Nullable Collection<MovieGenre> genres,
                       @Param("releaseYear") @Nullable Integer releaseYear,
                       @Param("includeArchived") boolean includeArchived,
                       Pageable pageable);
}
