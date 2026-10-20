package tp.appsinter.cine.inventory;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findByUuidIn(Collection<UUID> uuids);

    /**
     * Búsqueda de productos por categoría y por parte del nombre; cualquier filtro nulo no filtra.
     *
     * @param pattern         patrón «contiene» ya escapado ({@code CriteriaSupport.containsPattern}),
     *                        o {@code null} para no filtrar por nombre
     * @param includeArchived si es falso, los productos archivados no aparecen
     */
    @Query("""
            select i
            from Inventory i
            where (:category is null or i.category = :category)
              and (:pattern is null or lower(i.productName) like cast(:pattern as String) escape '\\')
              and (:includeArchived = true or i.archivedAt is null)
            """)
    Page<Inventory> search(@Param("category") @Nullable ProductCategory category,
                           @Param("pattern") @Nullable String pattern,
                           @Param("includeArchived") boolean includeArchived,
                           Pageable pageable);

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
