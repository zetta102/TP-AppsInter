package tp.appsinter.cine.contact;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.contact.dto.response.ContactMessageResponseDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.util.UUID;

/**
 * Bandeja de administración de los mensajes de contacto. El rol se exige en {@code SecurityConfig},
 * junto con el resto de las reglas de acceso.
 */
@RestController
@RequestMapping("/api/admin/contact-messages")
@RequiredArgsConstructor
@Tag(name = "Contact messages (admin)")
public class ContactMessageAdminController {

    private final ContactMessageService contactMessageService;

    @GetMapping
    public PageResponseDTO<ContactMessageResponseDTO> findAll(
            @RequestParam(required = false) ContactCategory category,
            @Parameter(description = "true for read messages only, false for unread only")
            @RequestParam(required = false) Boolean read,
            @Parameter(description = "Part of the author's e-mail, case-insensitive")
            @RequestParam(required = false) String email,
            @ParameterObject @AllowedSort(value = {"createdAt", "readAt", "category"}, aliases = {
                    @AllowedSort.Alias(name = "userEmail", property = "user.email")}) Pageable pageable) {
        return contactMessageService.findAll(category, read, email, pageable);
    }

    /**
     * Abre un mensaje. La primera apertura lo marca como leído y el autor ya no puede corregirlo.
     */
    @GetMapping("/{uuid}")
    public ContactMessageResponseDTO findById(@PathVariable UUID uuid) {
        return contactMessageService.open(uuid);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID uuid) {
        contactMessageService.delete(uuid);
    }
}
