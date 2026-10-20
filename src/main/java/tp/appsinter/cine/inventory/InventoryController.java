package tp.appsinter.cine.inventory;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.inventory.dto.request.InventoryAdjustmentRequestDTO;
import tp.appsinter.cine.inventory.dto.request.InventoryItemRequestDTO;
import tp.appsinter.cine.inventory.dto.response.InventoryAdjustmentResponseDTO;
import tp.appsinter.cine.inventory.dto.response.InventoryItemResponseDTO;
import tp.appsinter.cine.shared.dto.request.ArchiveRequestDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<InventoryItemResponseDTO> create(
            Authentication authentication,
            @Valid @RequestBody InventoryItemRequestDTO request) {
        InventoryItemResponseDTO created = inventoryService.create(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/inventory/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<InventoryItemResponseDTO> findAll(
            @RequestParam(required = false) @Nullable ProductCategory category,
            @RequestParam(required = false) @Nullable String search,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @ParameterObject @AllowedSort({"productName", "category", "cost", "quantity"}) Pageable pageable) {
        return inventoryService.findAll(category, search, includeArchived, pageable);
    }

    @GetMapping("/{uuid}")
    public InventoryItemResponseDTO findById(@PathVariable UUID uuid) {
        return inventoryService.findById(uuid);
    }

    @PutMapping("/{uuid}")
    public InventoryItemResponseDTO update(Authentication authentication,
                                             @PathVariable UUID uuid,
                                             @Valid @RequestBody InventoryItemRequestDTO request) {
        return inventoryService.update(uuid, request, authentication.getName());
    }

    /**
     * Archiva o restaura el producto con {@code {"archived": true|false}}. Uno archivado deja de
     * figurar en la carta y de poder pedirse, y sólo aparece acá con {@code includeArchived=true}.
     */
    @PatchMapping("/{uuid}")
    public InventoryItemResponseDTO archive(@PathVariable UUID uuid, @Valid @RequestBody ArchiveRequestDTO request) {
        return inventoryService.archive(uuid, request.archived());
    }

    /**
     * Borra un producto sin historia. Uno que ya se vendió o tiene movimientos de stock responde
     * {@code 409 RESOURCE_IN_USE} y se archiva en su lugar.
     */
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        inventoryService.delete(uuid);
    }

    /**
     * Suma o resta stock de forma atómica.
     *
     * <p>Es la alternativa a releer el producto, calcular la cantidad final y volver a escribirla:
     * ese camino pierde ajustes simultáneos o los rechaza por conflicto de versión.
     */
    @PostMapping("/{uuid}/adjustments")
    public InventoryItemResponseDTO adjustStock(Authentication authentication,
                                                  @PathVariable UUID uuid,
                                                  @Valid @RequestBody InventoryAdjustmentRequestDTO request) {
        return inventoryService.adjustStock(uuid, request, authentication.getName());
    }

    /**
     * Historial de movimientos de stock del producto; por omisión, del más reciente al más antiguo.
     */
    @GetMapping("/{uuid}/adjustments")
    public PageResponseDTO<InventoryAdjustmentResponseDTO> findAdjustments(
            @PathVariable UUID uuid,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            @AllowedSort({"createdAt", "delta", "resultingStock"}) Pageable pageable) {
        return inventoryService.findAdjustments(uuid, pageable);
    }
}
