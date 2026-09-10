package tp.appsinter.cine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.MovieScreening;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovieScreeningRepository extends JpaRepository<MovieScreening, UUID> {

    List<MovieScreening> findByMovieUuid(UUID movieUuid);

    List<MovieScreening> findByStartsAtAfterOrderByStartsAtAsc(OffsetDateTime from);

    @Query("""
            select s
            from MovieScreening s
            where (:movieUuid is null or s.movie.uuid = :movieUuid)
              and (:roomUuid is null or s.screenRoom.uuid = :roomUuid)
              and (:from is null or s.startsAt >= :from)
              and (:to is null or s.startsAt <= :to)
            """)
    Page<MovieScreening> search(@Param("movieUuid") UUID movieUuid,
                                @Param("roomUuid") UUID roomUuid,
                                @Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                Pageable pageable);

    /**
     * Devuelve la primera función que se superpone con la ventana pedida, para poder nombrarla en
     * el contexto del error {@code SCREENING_OVERLAP} en lugar de limitarse a avisar del choque.
     */
    @Query("""
            select s
            from MovieScreening s
            where s.screenRoom.uuid = :roomUuid
              and (:excludedUuid is null or s.uuid <> :excludedUuid)
              and s.startsAt < :endsAt
              and s.endsAt > :startsAt
            order by s.startsAt asc
            limit 1
            """)
    Optional<MovieScreening> findFirstOverlapInRoom(@Param("roomUuid") UUID roomUuid,
                                                    @Param("startsAt") OffsetDateTime startsAt,
                                                    @Param("endsAt") OffsetDateTime endsAt,
                                                    @Param("excludedUuid") UUID excludedUuid);
}
