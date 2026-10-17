package tp.appsinter.cine.inventory;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.inventory.dto.ProductResponseDTO;
import tp.appsinter.cine.shared.dto.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.util.UUID;

/**
 * Catálogo público de confitería.
 *
 * <p>Existe en paralelo a {@code /api/inventory}, que sigue siendo exclusivo de administración. La
 * carta es información pública, igual que la cartelera, pero el stock y el costo no lo son: por eso
 * hay dos recursos y no un solo endpoint con permisos relajados.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Catálogo público de confitería")
public class ProductController {

    private final InventoryService inventoryService;

    /**
     * Permite ordenar por {@code price} aunque la columna se llame {@code cost}: el cliente no tiene
     * por qué conocer el nombre interno del campo que este recurso justamente le oculta. El stock no
     * se publica, así que tampoco se puede ordenar por él.
     */
    @GetMapping
    public PageResponseDTO<ProductResponseDTO> findAll(
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) String search,
            @ParameterObject @AllowedSort(value = {"productName", "category"},
                    aliases = @AllowedSort.Alias(name = "price", property = "cost")) Pageable pageable) {
        return inventoryService.findAllProducts(category, search, pageable);
    }

    @GetMapping("/{uuid}")
    public ProductResponseDTO findById(@PathVariable UUID uuid) {
        return inventoryService.findProductById(uuid);
    }
}
