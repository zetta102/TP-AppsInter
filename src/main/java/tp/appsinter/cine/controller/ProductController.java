package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.dto.response.ProductResponseDTO;
import tp.appsinter.cine.model.utils.ProductCategory;
import tp.appsinter.cine.service.InventoryService;

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

    /**
     * Permite ordenar por {@code price} aunque la columna se llame {@code cost}: el cliente no tiene
     * por qué conocer el nombre interno del campo que este recurso justamente le oculta. El stock no
     * se publica, así que tampoco se puede ordenar por él.
     */
    private static final SortWhitelist SORTABLE = SortWhitelist.of("productName", "category")
            .alias("price", "cost");

    private final InventoryService inventoryService;

    @GetMapping
    public PageResponseDTO<ProductResponseDTO> findAll(
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) String search,
            @ParameterObject Pageable pageable) {
        return inventoryService.findAllProducts(category, search, SORTABLE.apply(pageable));
    }

    @GetMapping("/{uuid}")
    public ProductResponseDTO findById(@PathVariable UUID uuid) {
        return inventoryService.findProductById(uuid);
    }
}
