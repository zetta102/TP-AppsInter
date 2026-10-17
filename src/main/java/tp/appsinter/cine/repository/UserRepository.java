package tp.appsinter.cine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.User;
import tp.appsinter.cine.model.utils.Role;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    /**
     * Administradores activos. Es la comprobación que impide que el sistema quede sin quien lo
     * administre.
     */
    long countByRoleAndDeletedAtIsNull(Role role);

}
