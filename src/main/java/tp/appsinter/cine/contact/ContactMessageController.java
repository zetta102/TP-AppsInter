package tp.appsinter.cine.contact;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.contact.dto.request.ContactMessageRequestDTO;
import tp.appsinter.cine.contact.dto.response.ContactMessageResponseDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.net.URI;
import java.util.UUID;

/**
 * Formulario de contacto visto por su autor: enviar, consultar y corregir los propios mensajes.
 *
 * <p>Un mensaje ajeno responde 404. La bandeja de administración vive en
 * {@link ContactMessageAdminController}.
 */
@RestController
@RequestMapping("/api/contact-messages")
@RequiredArgsConstructor
@Tag(name = "Contact messages")
public class ContactMessageController {

    private final ContactMessageService contactMessageService;

    @PostMapping
    public ResponseEntity<ContactMessageResponseDTO> create(Authentication authentication,
                                                            @Valid @RequestBody ContactMessageRequestDTO request) {
        ContactMessageResponseDTO created = contactMessageService.create(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/contact-messages/" + created.uuid())).body(created);
    }

    @GetMapping
    public PageResponseDTO<ContactMessageResponseDTO> findMine(
            Authentication authentication,
            @RequestParam(required = false) ContactCategory category,
            @ParameterObject @AllowedSort({"createdAt", "category"}) Pageable pageable) {
        return contactMessageService.findMine(authentication.getName(), category, pageable);
    }

    @GetMapping("/{uuid}")
    public ContactMessageResponseDTO findById(Authentication authentication, @PathVariable UUID uuid) {
        return contactMessageService.findMineById(uuid, authentication.getName());
    }

    /**
     * Reemplaza el contenido del mensaje. Responde 422 {@code MESSAGE_ALREADY_READ} si
     * administración ya lo leyó.
     */
    @PutMapping("/{uuid}")
    public ContactMessageResponseDTO update(Authentication authentication,
                                            @PathVariable UUID uuid,
                                            @Valid @RequestBody ContactMessageRequestDTO request) {
        return contactMessageService.update(uuid, authentication.getName(), request);
    }
}
