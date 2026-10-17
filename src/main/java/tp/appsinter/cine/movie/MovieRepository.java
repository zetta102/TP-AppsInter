package tp.appsinter.cine.movie;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.screening.MovieScreeningRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {

    /**
     * Búsqueda del catálogo. Los géneros se combinan con «o»: la película entra si tiene alguno de
     * los pedidos, que es lo que espera un selector múltiple.
     *
     * @param genres {@code null} para no filtrar por género; una lista vacía no se admite porque
     *               no dejaría pasar nada
     * @param includeArchived si es falso, las películas archivadas no aparecen
     */
    @Query("""
            select distinct m
            from Movie m
            left join m.genres g
            where (:search is null or lower(m.movieName) like lower(concat('%', cast(:search as String), '%')))
              and (:genres is null or g in :genres)
              and (:releaseYear is null or m.releaseYear = :releaseYear)
              and (:includeArchived = true or m.archivedAt is null)
            """)
    Page<Movie> search(@Param("search") @Nullable String search,
                       @Param("genres") @Nullable Collection<MovieGenre> genres,
                       @Param("releaseYear") @Nullable Integer releaseYear,
                       @Param("includeArchived") boolean includeArchived,
                       Pageable pageable);

    /**
     * Películas vigentes con al menos una función vigente que empieza dentro de la ventana. Con
     * {@code to} nulo la ventana no tiene fin (ver {@link MovieScreeningRepository#search} sobre
     * {@code coalesce}).
     */
    @Query("""
            select m
            from Movie m
            where m.archivedAt is null
              and exists (select 1
                          from MovieScreening s
                          where s.movie = m
                            and s.archivedAt is null
                            and s.startsAt >= :from
                            and s.startsAt <= coalesce(:to, s.startsAt))
            """)
    Page<Movie> findNowShowing(@Param("from") OffsetDateTime from,
                               @Param("to") @Nullable OffsetDateTime to,
                               Pageable pageable);
}
