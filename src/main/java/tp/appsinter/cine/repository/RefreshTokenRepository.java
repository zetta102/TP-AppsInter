package tp.appsinter.cine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.RefreshToken;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :revokedAt
             where t.user.uuid = :userUuid
               and t.revokedAt is null
            """)
    int revokeAllForUser(@Param("userUuid") UUID userUuid, @Param("revokedAt") OffsetDateTime revokedAt);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff);
}
