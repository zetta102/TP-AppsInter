package tp.appsinter.cine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.Movie;
import tp.appsinter.cine.model.utils.MovieGenre;

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
     */
    @Query("""
            select distinct m
            from Movie m
            left join m.genres g
            where (:search is null or lower(m.movieName) like lower(concat('%', cast(:search as String), '%')))
              and (:genres is null or g in :genres)
              and (:releaseYear is null or m.releaseYear = :releaseYear)
            """)
    Page<Movie> search(@Param("search") String search,
                       @Param("genres") Collection<MovieGenre> genres,
                       @Param("releaseYear") Integer releaseYear,
                       Pageable pageable);
}
