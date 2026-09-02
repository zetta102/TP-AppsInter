package tp.appsinter.cine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.Inventory;
import tp.appsinter.cine.model.utils.ProductCategory;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findByCategory(ProductCategory category);

    List<Inventory> findByUuidIn(List<UUID> uuids);

    Page<Inventory> findByCategory(ProductCategory category, Pageable pageable);

    Page<Inventory> findByProductNameContainingIgnoreCase(String productName, Pageable pageable);

    Page<Inventory> findByCategoryAndProductNameContainingIgnoreCase(
            ProductCategory category, String productName, Pageable pageable);

    boolean existsByProductNameIgnoreCase(String productName);

    boolean existsByProductNameIgnoreCaseAndUuidNot(String productName, UUID uuid);
}
