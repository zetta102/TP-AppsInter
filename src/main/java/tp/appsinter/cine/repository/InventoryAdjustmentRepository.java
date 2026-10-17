package tp.appsinter.cine.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tp.appsinter.cine.model.entities.InventoryAdjustment;

import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    Page<InventoryAdjustment> findByInventoryUuid(UUID inventoryUuid, Pageable pageable);
}
