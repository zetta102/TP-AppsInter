package tp.appsinter.cine.inventory;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.inventory.dto.CreateInventoryRequestDTO;
import tp.appsinter.cine.inventory.dto.InventoryAdjustmentRequestDTO;
import tp.appsinter.cine.inventory.dto.InventoryAdjustmentResponseDTO;
import tp.appsinter.cine.inventory.dto.InventoryResponseDTO;
import tp.appsinter.cine.shared.dto.ArchiveRequestDTO;
import tp.appsinter.cine.shared.dto.PageResponseDTO;
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
    public ResponseEntity<InventoryResponseDTO> create(
            @Valid @RequestBody CreateInventoryRequestDTO request) {
        InventoryResponseDTO created = inventoryService.create(request);
        return ResponseEntity.created(URI.create("/api/inventory/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<InventoryResponseDTO> findAll(
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @ParameterObject @AllowedSort({"productName", "category", "cost", "quantity"}) Pageable pageable) {
        return inventoryService.findAll(category, search, includeArchived, pageable);
    }

    @GetMapping("/{uuid}")
    public InventoryResponseDTO findById(@PathVariable UUID uuid) {
        return inventoryService.findById(uuid);
    }

    @PutMapping("/{uuid}")
    public InventoryResponseDTO update(Authentication authentication,
                                             @PathVariable UUID uuid,
                                             @Valid @RequestBody CreateInventoryRequestDTO request) {
        return inventoryService.update(uuid, request,
                authentication == null ? null : authentication.getName());
    }

    /**
     * Archiva o restaura el producto con {@code {"archived": true|false}}. Uno archivado deja de
     * figurar en la carta y de poder pedirse, y sólo aparece acá con {@code includeArchived=true}.
     */
    @PatchMapping("/{uuid}")
    public InventoryResponseDTO archive(@PathVariable UUID uuid, @Valid @RequestBody ArchiveRequestDTO request) {
        return inventoryService.archive(uuid, request.archived());
    }

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
    public InventoryResponseDTO adjustStock(Authentication authentication,
                                                  @PathVariable UUID uuid,
                                                  @Valid @RequestBody InventoryAdjustmentRequestDTO request) {
        return inventoryService.adjustStock(uuid, request,
                authentication == null ? null : authentication.getName());
    }

    /**
     * Historial de movimientos de stock del producto.
     */
    @GetMapping("/{uuid}/adjustments")
    public PageResponseDTO<InventoryAdjustmentResponseDTO> findAdjustments(
            @PathVariable UUID uuid,
            @ParameterObject @AllowedSort({"createdAt", "delta", "resultingStock"}) Pageable pageable) {
        return inventoryService.findAdjustments(uuid, pageable);
    }
}
