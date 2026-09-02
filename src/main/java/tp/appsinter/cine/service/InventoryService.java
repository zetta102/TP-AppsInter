package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateInventoryRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateInventoryResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.entities.Inventory;
import tp.appsinter.cine.model.utils.ProductCategory;
import tp.appsinter.cine.repository.InventoryRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public CreateInventoryResponseDTO create(CreateInventoryRequestDTO request) {
        if (inventoryRepository.existsByProductNameIgnoreCase(request.productName())) {
            throw new ConflictException("Product '%s' already exists".formatted(request.productName()));
        }
        Inventory inventory = Inventory.builder()
                .productName(request.productName())
                .category(request.category())
                .cost(request.cost())
                .quantity(request.quantity())
                .build();
        return CreateInventoryResponseDTO.from(inventoryRepository.save(inventory));
    }

    public PageResponseDTO<CreateInventoryResponseDTO> findAll(ProductCategory category,
                                                               String search,
                                                               Pageable pageable) {
        String term = search == null || search.isBlank() ? null : search.trim();
        Page<Inventory> page;
        if (category != null && term != null) {
            page = inventoryRepository.findByCategoryAndProductNameContainingIgnoreCase(category, term, pageable);
        } else if (category != null) {
            page = inventoryRepository.findByCategory(category, pageable);
        } else if (term != null) {
            page = inventoryRepository.findByProductNameContainingIgnoreCase(term, pageable);
        } else {
            page = inventoryRepository.findAll(pageable);
        }
        return PageResponseDTO.from(page, CreateInventoryResponseDTO::from);
    }

    public CreateInventoryResponseDTO findById(UUID uuid) {
        return CreateInventoryResponseDTO.from(getInventory(uuid));
    }

    @Transactional
    public CreateInventoryResponseDTO update(UUID uuid, CreateInventoryRequestDTO request) {
        Inventory inventory = getInventory(uuid);
        if (inventoryRepository.existsByProductNameIgnoreCaseAndUuidNot(request.productName(), uuid)) {
            throw new ConflictException("Product '%s' already exists".formatted(request.productName()));
        }
        inventory.setProductName(request.productName());
        inventory.setCategory(request.category());
        inventory.setCost(request.cost());
        inventory.setQuantity(request.quantity());
        return CreateInventoryResponseDTO.from(inventory);
    }

    @Transactional
    public void delete(UUID uuid) {
        inventoryRepository.delete(getInventory(uuid));
    }

    private Inventory getInventory(UUID uuid) {
        return inventoryRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Inventory product", uuid));
    }
}
