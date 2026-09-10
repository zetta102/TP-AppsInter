package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
     * Nombre del campo tal como lo ve el cliente, y la propiedad de la entidad que hay detrás.
     */
    private static final String PUBLIC_PRICE_PROPERTY = "price";
    private static final String ENTITY_PRICE_PROPERTY = "cost";

    private final InventoryService inventoryService;

    @GetMapping
    public PageResponseDTO<ProductResponseDTO> findAll(
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "productName") Pageable pageable) {
        return inventoryService.findAllProducts(category, search, translateSort(pageable));
    }

    @GetMapping("/{uuid}")
    public ProductResponseDTO findById(@PathVariable UUID uuid) {
        return inventoryService.findProductById(uuid);
    }

    /**
     * Permite ordenar por {@code price} aunque la columna se llame {@code cost}. Sin esta
     * traducción el cliente tendría que conocer el nombre interno del campo que este recurso
     * justamente le oculta.
     */
    private Pageable translateSort(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.getOrderFor(PUBLIC_PRICE_PROPERTY) == null) {
            return pageable;
        }
        Sort translated = Sort.by(sort.stream()
                .map(order -> PUBLIC_PRICE_PROPERTY.equals(order.getProperty())
                        ? order.withProperty(ENTITY_PRICE_PROPERTY)
                        : order)
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translated);
    }
}
