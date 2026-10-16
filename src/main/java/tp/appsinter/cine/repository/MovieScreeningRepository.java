package tp.appsinter.cine.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.MovieScreening;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovieScreeningRepository extends JpaRepository<MovieScreening, UUID> {

    /**
     * Lee la función bloqueando su fila hasta el final de la transacción.
     *
     * <p>Serializa las operaciones que deciden la ocupación de una misma función —bloquear butacas y
     * confirmar reservas—, cuyo «comprobar y después escribir» no está protegido por ningún índice
     * único en el caso de los bloqueos. Funciones distintas no se esperan entre sí.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MovieScreening s where s.uuid = :uuid")
    Optional<MovieScreening> findByIdForUpdate(@Param("uuid") UUID uuid);

    /**
     * Los límites de fecha usan {@code coalesce} en lugar de {@code :from is null}: PostgreSQL no
     * puede inferir el tipo de un parámetro {@code OffsetDateTime} en {@code ? is null} y falla con
     * «could not determine data type of parameter». Con {@code coalesce} el tipo sale de
     * {@code s.startsAt}, y un límite nulo compara la columna consigo misma (siempre verdadero, porque
     * {@code startsAt} no admite nulos).
     */
    @Query("""
            select s
            from MovieScreening s
            where (:movieUuid is null or s.movie.uuid = :movieUuid)
              and (:roomUuid is null or s.screenRoom.uuid = :roomUuid)
              and s.startsAt >= coalesce(:from, s.startsAt)
              and s.startsAt <= coalesce(:to, s.startsAt)
              and (:includeArchived = true or s.archivedAt is null)
            """)
    Page<MovieScreening> search(@Param("movieUuid") UUID movieUuid,
                                @Param("roomUuid") UUID roomUuid,
                                @Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                @Param("includeArchived") boolean includeArchived,
                                Pageable pageable);

    /**
     * Funciones vigentes de las películas pedidas dentro de la ventana, en orden cronológico. Es la
     * segunda mitad de la cartelera agregada: la primera pagina las películas
     * ({@link MovieRepository#findNowShowing}).
     */
    @Query("""
            select s
            from MovieScreening s
            join fetch s.movie
            join fetch s.screenRoom
            where s.movie.uuid in :movieUuids
              and s.archivedAt is null
              and s.startsAt >= :from
              and s.startsAt <= coalesce(:to, s.startsAt)
            order by s.startsAt asc
            """)
    List<MovieScreening> findShowingForMovies(@Param("movieUuids") Collection<UUID> movieUuids,
                                              @Param("from") OffsetDateTime from,
                                              @Param("to") OffsetDateTime to);

    /**
     * Si la película tiene alguna función vigente que todavía no terminó. Bloquea archivarla: la
     * cartelera seguiría ofreciendo funciones de una película que ya no figura.
     */
    boolean existsByMovieUuidAndArchivedAtIsNullAndEndsAtAfter(UUID movieUuid, OffsetDateTime now);

    /**
     * Si la sala tiene alguna función vigente que todavía no terminó.
     */
    boolean existsByScreenRoomUuidAndArchivedAtIsNullAndEndsAtAfter(UUID roomUuid, OffsetDateTime now);

    /**
     * Devuelve la primera función que se superpone con la ventana pedida, para poder nombrarla en
     * el contexto del error {@code SCREENING_OVERLAP} en lugar de limitarse a avisar del choque.
     * Las funciones archivadas no ocupan la sala.
     */
    @Query("""
            select s
            from MovieScreening s
            where s.screenRoom.uuid = :roomUuid
              and s.archivedAt is null
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
