package tp.appsinter.cine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.Movie;
import tp.appsinter.cine.model.utils.MovieGenre;

import java.util.UUID;

@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {

    @Query("""
            select distinct m
            from Movie m
            left join m.genres g
            where (:search is null or lower(m.movieName) like lower(concat('%', cast(:search as String), '%')))
              and (:genre is null or g = :genre)
            """)
    Page<Movie> search(@Param("search") String search,
                       @Param("genre") MovieGenre genre,
                       Pageable pageable);
}
