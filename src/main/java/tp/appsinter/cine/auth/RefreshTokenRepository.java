package tp.appsinter.cine.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :revokedAt, t.revokedReason = :reason
             where t.user.uuid = :userUuid
               and t.revokedAt is null
            """)
    int revokeAllForUser(@Param("userUuid") UUID userUuid,
                         @Param("revokedAt") OffsetDateTime revokedAt,
                         @Param("reason") RevocationReason reason);

    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :revokedAt, t.revokedReason = :reason
             where t.user.uuid = :userUuid
               and t.revokedAt is null
               and t.tokenHash <> :keepTokenHash
            """)
    int revokeAllForUserExcept(@Param("userUuid") UUID userUuid,
                               @Param("keepTokenHash") String keepTokenHash,
                               @Param("revokedAt") OffsetDateTime revokedAt,
                               @Param("reason") RevocationReason reason);

    @Modifying
    @Query("""
            update RefreshToken t
               set t.revokedAt = :revokedAt, t.revokedReason = :reason
             where t.familyId = :familyId
               and t.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("revokedAt") OffsetDateTime revokedAt,
                     @Param("reason") RevocationReason reason);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff);
}
