package tp.appsinter.cine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.Inventory;
import tp.appsinter.cine.model.utils.ProductCategory;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findByUuidIn(List<UUID> uuids);

    Page<Inventory> findByCategory(ProductCategory category, Pageable pageable);

    Page<Inventory> findByProductNameContainingIgnoreCase(String productName, Pageable pageable);

    Page<Inventory> findByCategoryAndProductNameContainingIgnoreCase(
            ProductCategory category, String productName, Pageable pageable);

    boolean existsByProductNameIgnoreCase(String productName);

    boolean existsByProductNameIgnoreCaseAndUuidNot(String productName, UUID uuid);

    /**
     * Aplica una variación de stock en una sola sentencia.
     *
     * <p>Sumar en la base y no en memoria es lo que permite que dos reposiciones simultáneas se
     * compongan en lugar de pisarse. La condición sobre el resultado impide que el stock quede
     * negativo sin necesidad de leer primero, y devolver cero filas es la señal de que no alcanzaba.
     *
     * <p>Actualiza también la versión optimista a mano: la sentencia esquiva el ciclo de vida de
     * JPA, y dejar la versión quieta haría que una entidad ya cargada creyera estar al día.
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET quantity = quantity + :delta,
                   version = version + 1
             WHERE uuid = :uuid
               AND quantity + :delta >= 0
            """, nativeQuery = true)
    int applyDelta(@Param("uuid") UUID uuid, @Param("delta") int delta);
}
