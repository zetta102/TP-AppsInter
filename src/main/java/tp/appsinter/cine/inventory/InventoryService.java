package tp.appsinter.cine.inventory;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.inventory.dto.request.InventoryAdjustmentRequestDTO;
import tp.appsinter.cine.inventory.dto.request.InventoryItemRequestDTO;
import tp.appsinter.cine.inventory.dto.response.InventoryAdjustmentResponseDTO;
import tp.appsinter.cine.inventory.dto.response.InventoryItemResponseDTO;
import tp.appsinter.cine.inventory.dto.response.ProductResponseDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.ConflictException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.shared.persistence.CriteriaSupport;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    /**
     * Motivo con que se asienta la carga de stock de un producto nuevo.
     */
    static final String INITIAL_STOCK = "Initial stock";

    /**
     * Motivo con que se asienta un cambio de stock hecho al editar el producto completo.
     */
    static final String STOCK_SET_BY_UPDATE = "Stock set by product update";

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final ProductSales productSales;
    private final EntityManager entityManager;
    private final Clock clock;

    /**
     * Aplica una variación de stock y la deja asentada.
     *
     * <p>Se pide la variación en lugar de la cantidad final porque dos reposiciones simultáneas
     * tienen que sumarse. La suma ocurre en la base, en una sola sentencia condicionada a que el
     * resultado no quede negativo, de modo que no hay lectura previa que pueda quedar vieja ni
     * conflicto de versión que reintentar. Una variación de cero ya la rechaza la validación.
     */
    @Transactional
    public InventoryItemResponseDTO adjustStock(UUID uuid, InventoryAdjustmentRequestDTO request, String adjustedBy) {
        Inventory inventory = getInventory(uuid);

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

        record(inventory, request.delta(), request.reason(), adjustedBy);
        return InventoryItemResponseDTO.from(inventory);
    }

    /**
     * Historial de movimientos de un producto, en el orden que pida el listado; por omisión, del
     * más reciente al más antiguo.
     */
    public PageResponseDTO<InventoryAdjustmentResponseDTO> findAdjustments(UUID uuid, Pageable pageable) {
        getInventory(uuid);
        return PageResponseDTO.from(
                inventoryAdjustmentRepository.findByInventoryUuid(uuid, pageable),
                InventoryAdjustmentResponseDTO::from);
    }

    /**
     * Da de alta un producto. Si llega con stock, la carga inicial queda asentada en el historial
     * como el primer movimiento, igual que cualquier ajuste posterior.
     */
    @Transactional
    public InventoryItemResponseDTO create(InventoryItemRequestDTO request, String createdBy) {
        if (inventoryRepository.existsByProductNameIgnoreCase(request.productName())) {
            throw ConflictException.nameTaken(
                    "Product '%s' already exists".formatted(request.productName()),
                    "productName", request.productName());
        }
        Inventory inventory = inventoryRepository.save(Inventory.builder()
                .productName(request.productName())
                .category(request.category())
                .cost(request.cost())
                .quantity(request.quantity())
                .build());
        if (request.quantity() > 0) {
            record(inventory, request.quantity(), INITIAL_STOCK, createdBy);
        }
        return InventoryItemResponseDTO.from(inventory);
    }

    public PageResponseDTO<InventoryItemResponseDTO> findAll(@Nullable ProductCategory category,
                                                             @Nullable String search,
                                                             boolean includeArchived,
                                                             Pageable pageable) {
        return PageResponseDTO.from(search(category, search, includeArchived, pageable),
                InventoryItemResponseDTO::from);
    }

    /**
     * Misma búsqueda que {@link #findAll}, pero proyectada al catálogo público: sin stock, y nunca
     * con productos archivados.
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
        return inventoryRepository.search(category, CriteriaSupport.containsPattern(search), includeArchived, pageable);
    }

    public InventoryItemResponseDTO findById(UUID uuid) {
        return InventoryItemResponseDTO.from(getInventory(uuid));
    }

    /**
     * Corrige los datos del producto.
     *
     * <p>Si la cantidad cambia, la diferencia queda asentada en el historial como cualquier otro
     * ajuste: de lo contrario un {@code PUT} podría mover el stock sin dejar rastro. La escritura
     * está protegida por {@code @Version}: si una venta simultánea toca el producto, el intento se
     * deshace y se repite sobre el stock fresco, igual que la compra.
     */
    @Retryable(includes = OptimisticLockingFailureException.class,
            maxRetries = 3, delay = 50, jitter = 25, multiplier = 2)
    @Transactional
    public InventoryItemResponseDTO update(UUID uuid, InventoryItemRequestDTO request, String adjustedBy) {
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
            record(inventory, delta, STOCK_SET_BY_UPDATE, adjustedBy);
        }
        // Se escribe acá para que un conflicto de versión aflore dentro del método y lo vea el
        // reintento, y no recién al confirmar, ya fuera de él.
        inventoryRepository.flush();
        return InventoryItemResponseDTO.from(inventory);
    }

    /**
     * Archiva o restaura un producto. Siempre se admite: las reservas que ya lo incluyen conservan
     * su línea, y desde ese momento deja de ofrecerse en la carta y de poder pedirse.
     */
    @Transactional
    public InventoryItemResponseDTO archive(UUID uuid, boolean archived) {
        Inventory inventory = getInventory(uuid);
        if (archived != inventory.isArchived()) {
            inventory.setArchivedAt(archived ? OffsetDateTime.now(clock) : null);
        }
        return InventoryItemResponseDTO.from(inventory);
    }

    /**
     * Borra un producto que no tiene historia: ni ventas ni movimientos de stock. Uno que la tiene
     * se archiva ({@code 409 RESOURCE_IN_USE}), para que el historial no pierda a qué se refiere.
     */
    @Transactional
    public void delete(UUID uuid) {
        Inventory inventory = getInventory(uuid);
        if (productSales.hasSales(uuid)) {
            throw ConflictException.resourceInUse(
                    "Product %s has been sold; archive it instead".formatted(uuid), "Product", "Reservation item");
        }
        if (inventoryAdjustmentRepository.existsByInventoryUuid(uuid)) {
            throw ConflictException.resourceInUse(
                    "Product %s has stock history; archive it instead".formatted(uuid),
                    "Product", "Inventory adjustment");
        }
        inventoryRepository.delete(inventory);
    }

    private void record(Inventory inventory, int delta, String reason, String adjustedBy) {
        inventoryAdjustmentRepository.save(InventoryAdjustment.builder()
                .inventory(inventory)
                .delta(delta)
                .resultingStock(inventory.getQuantity())
                .reason(reason)
                .adjustedBy(adjustedBy)
                .createdAt(OffsetDateTime.now(clock))
                .build());
    }

    private Inventory getInventory(UUID uuid) {
        return inventoryRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("Product", uuid));
    }
}
