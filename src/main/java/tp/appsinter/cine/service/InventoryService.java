package tp.appsinter.cine.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateInventoryRequestDTO;
import tp.appsinter.cine.model.dto.request.InventoryAdjustmentRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateInventoryResponseDTO;
import tp.appsinter.cine.model.dto.response.InventoryAdjustmentResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.dto.response.ProductResponseDTO;
import tp.appsinter.cine.model.entities.Inventory;
import tp.appsinter.cine.model.entities.InventoryAdjustment;
import tp.appsinter.cine.model.utils.ProductCategory;
import tp.appsinter.cine.repository.InventoryAdjustmentRepository;
import tp.appsinter.cine.repository.InventoryRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    /**
     * Motivo con que se asienta un cambio de stock hecho al editar el producto completo.
     */
    static final String STOCK_SET_BY_UPDATE = "Stock set by product update";

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final EntityManager entityManager;

    /**
     * Aplica una variación de stock y la deja asentada.
     *
     * <p>Se pide la variación en lugar de la cantidad final porque dos reposiciones simultáneas
     * tienen que sumarse. La suma ocurre en la base, en una sola sentencia condicionada a que el
     * resultado no quede negativo, de modo que no hay lectura previa que pueda quedar vieja ni
     * conflicto de versión que reintentar.
     */
    @Transactional
    public CreateInventoryResponseDTO adjustStock(UUID uuid,
                                                  InventoryAdjustmentRequestDTO request,
                                                  @Nullable String adjustedBy) {
        Inventory inventory = getInventory(uuid);
        if (request.delta() == 0) {
            throw BusinessRuleException.emptyAdjustment();
        }

        // La sentencia esquiva el contexto de persistencia; sin este vaciado, los cambios pendientes
        // podrían escribirse después y volver a pisar la cantidad con el valor viejo.
        entityManager.flush();
        int updated = inventoryRepository.applyDelta(uuid, request.delta());
        if (updated == 0) {
            throw BusinessRuleException.insufficientStock(
                    "Adjusting %s by %d would leave a negative stock".formatted(
                            inventory.getProductName(), request.delta()),
                    uuid, Math.abs(request.delta()), inventory.getQuantity());
        }
        entityManager.refresh(inventory);

        inventoryAdjustmentRepository.save(InventoryAdjustment.builder()
                .inventory(inventory)
                .delta(request.delta())
                .resultingStock(inventory.getQuantity())
                .reason(request.reason())
                .adjustedBy(adjustedBy)
                .createdAt(OffsetDateTime.now())
                .build());
        return CreateInventoryResponseDTO.from(inventory);
    }

    /**
     * Historial de movimientos de un producto, del más reciente al más antiguo.
     */
    public PageResponseDTO<InventoryAdjustmentResponseDTO> findAdjustments(UUID uuid, Pageable pageable) {
        getInventory(uuid);
        return PageResponseDTO.from(
                inventoryAdjustmentRepository.findByInventoryUuid(uuid, pageable),
                InventoryAdjustmentResponseDTO::from);
    }

    @Transactional
    public CreateInventoryResponseDTO create(CreateInventoryRequestDTO request) {
        if (inventoryRepository.existsByProductNameIgnoreCase(request.productName())) {
            throw ConflictException.nameTaken(
                    "Product '%s' already exists".formatted(request.productName()),
                    "productName", request.productName());
        }
        Inventory inventory = Inventory.builder()
                .productName(request.productName())
                .category(request.category())
                .cost(request.cost())
                .quantity(request.quantity())
                .build();
        return CreateInventoryResponseDTO.from(inventoryRepository.save(inventory));
    }

    public PageResponseDTO<CreateInventoryResponseDTO> findAll(@Nullable ProductCategory category,
                                                               @Nullable String search,
                                                               boolean includeArchived,
                                                               Pageable pageable) {
        return PageResponseDTO.from(search(category, search, includeArchived, pageable),
                CreateInventoryResponseDTO::from);
    }

    /**
     * Misma búsqueda que {@link #findAll}, pero proyectada al catálogo público: sin stock ni costo,
     * y nunca con productos archivados.
     */
    public PageResponseDTO<ProductResponseDTO> findAllProducts(@Nullable ProductCategory category,
                                                               @Nullable String search,
                                                               Pageable pageable) {
        return PageResponseDTO.from(search(category, search, false, pageable), ProductResponseDTO::from);
    }

    public ProductResponseDTO findProductById(UUID uuid) {
        return ProductResponseDTO.from(getInventory(uuid));
    }

    private Page<Inventory> search(@Nullable ProductCategory category,
                                   @Nullable String search,
                                   boolean includeArchived,
                                   Pageable pageable) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return inventoryRepository.search(category, term, includeArchived, pageable);
    }

    public CreateInventoryResponseDTO findById(UUID uuid) {
        return CreateInventoryResponseDTO.from(getInventory(uuid));
    }

    /**
     * Corrige los datos del producto.
     *
     * <p>Si la cantidad cambia, la diferencia queda asentada en el historial como cualquier otro
     * ajuste: de lo contrario un {@code PUT} podría mover el stock sin dejar rastro. La escritura
     * sigue protegida por {@code @Version}, así que una venta simultánea produce un conflicto en
     * lugar de perderse.
     */
    @Transactional
    public CreateInventoryResponseDTO update(UUID uuid, CreateInventoryRequestDTO request, @Nullable String adjustedBy) {
        Inventory inventory = getInventory(uuid);
        if (inventoryRepository.existsByProductNameIgnoreCaseAndUuidNot(request.productName(), uuid)) {
            throw ConflictException.nameTaken(
                    "Product '%s' already exists".formatted(request.productName()),
                    "productName", request.productName());
        }
        int delta = request.quantity() - inventory.getQuantity();
        inventory.setProductName(request.productName());
        inventory.setCategory(request.category());
        inventory.setCost(request.cost());
        inventory.setQuantity(request.quantity());
        if (delta != 0) {
            inventoryAdjustmentRepository.save(InventoryAdjustment.builder()
                    .inventory(inventory)
                    .delta(delta)
                    .resultingStock(request.quantity())
                    .reason(STOCK_SET_BY_UPDATE)
                    .adjustedBy(adjustedBy)
                    .createdAt(OffsetDateTime.now())
                    .build());
        }
        return CreateInventoryResponseDTO.from(inventory);
    }

    /**
     * Archiva o restaura un producto. Siempre se admite: las reservas que ya lo incluyen conservan
     * su línea, y desde ese momento deja de ofrecerse en la carta y de poder pedirse.
     */
    @Transactional
    public CreateInventoryResponseDTO archive(UUID uuid, boolean archived) {
        Inventory inventory = getInventory(uuid);
        if (archived != inventory.isArchived()) {
            inventory.setArchivedAt(archived ? OffsetDateTime.now() : null);
        }
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
