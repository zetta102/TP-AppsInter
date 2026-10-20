package tp.appsinter.cine.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    Page<InventoryAdjustment> findByInventoryUuid(UUID inventoryUuid, Pageable pageable);

    boolean existsByInventoryUuid(UUID inventoryUuid);
}
