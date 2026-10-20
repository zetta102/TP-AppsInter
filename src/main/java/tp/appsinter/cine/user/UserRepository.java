package tp.appsinter.cine.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Administradores activos. Es la comprobación que impide que el sistema quede sin quien lo
     * administre, y la que decide si hace falta sembrar el primero.
     */
    long countByRoleAndDeletedAtIsNull(Role role);
}
