package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.request.CreateInventoryRequestDTO;
import tp.appsinter.cine.model.dto.request.InventoryAdjustmentRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateInventoryResponseDTO;
import tp.appsinter.cine.model.dto.response.InventoryAdjustmentResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.utils.ProductCategory;
import tp.appsinter.cine.service.InventoryService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<CreateInventoryResponseDTO> create(
            @Valid @RequestBody CreateInventoryRequestDTO request) {
        CreateInventoryResponseDTO created = inventoryService.create(request);
        return ResponseEntity.created(URI.create("/api/inventory/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<CreateInventoryResponseDTO> findAll(
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) String search,
            @Schema(description = "Página de productos", example = """
                    {
                      "page": 0,
                      "size": 20,
                      "sort": "productName"
                    }""") Pageable pageable) {
        return inventoryService.findAll(category, search, pageable);
    }

    @GetMapping("/{uuid}")
    public CreateInventoryResponseDTO findById(@PathVariable UUID uuid) {
        return inventoryService.findById(uuid);
    }

    @PutMapping("/{uuid}")
    public CreateInventoryResponseDTO update(@PathVariable UUID uuid,
                                             @Valid @RequestBody CreateInventoryRequestDTO request) {
        return inventoryService.update(uuid, request);
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
    public CreateInventoryResponseDTO adjustStock(Authentication authentication,
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
            @Schema(description = "Página de productos", example = """
                    {
                      "page": 0,
                      "size": 20,
                      "sort": "createdAt"
                    }""") Pageable pageable) {
        return inventoryService.findAdjustments(uuid, pageable);
    }
}
