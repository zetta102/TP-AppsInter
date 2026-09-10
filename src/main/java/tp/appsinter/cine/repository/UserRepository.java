package tp.appsinter.cine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.User;
import tp.appsinter.cine.model.utils.Role;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    Page<User> findByRole(Role role, Pageable pageable);

    /**
     * Administradores activos. Es la comprobación que impide que el sistema quede sin quien lo
     * administre.
     */
    long countByRoleAndDeletedAtIsNull(Role role);

    Page<User> findByDeletedAtIsNull(Pageable pageable);

    Page<User> findByRoleAndDeletedAtIsNull(Role role, Pageable pageable);
}
