package tp.appsinter.cine.contact;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.contact.dto.request.ContactMessageRequestDTO;
import tp.appsinter.cine.contact.dto.response.ContactMessageResponseDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.error.BusinessRuleException;
import tp.appsinter.cine.shared.error.NotFoundException;
import tp.appsinter.cine.user.User;
import tp.appsinter.cine.user.UserService;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactMessageService {

    private static final String RESOURCE = "Contact message";

    private final ContactMessageRepository contactMessageRepository;
    private final UserService userService;

    @Transactional
    public ContactMessageResponseDTO create(String userEmail, ContactMessageRequestDTO request) {
        User user = userService.getByEmail(userEmail);
        ContactMessage message = ContactMessage.builder()
                .user(user)
                .category(request.category())
                .subject(request.subject().strip())
                .body(request.body().strip())
                .build();
        return ContactMessageResponseDTO.from(contactMessageRepository.save(message));
    }

    /**
     * Mensajes del cliente autenticado. El filtro de autor se aplica acá y no en el controlador:
     * así ningún parámetro de consulta puede ampliarlo a los mensajes de otra persona.
     */
    public PageResponseDTO<ContactMessageResponseDTO> findMine(String userEmail,
                                                               @Nullable ContactCategory category,
                                                               Pageable pageable) {
        Specification<ContactMessage> spec = ContactMessageSpecifications.withAuthor()
                .and(ContactMessageSpecifications.ownedBy(userEmail))
                .and(ContactMessageSpecifications.hasCategory(category));
        return PageResponseDTO.from(contactMessageRepository.findAll(spec, pageable),
                ContactMessageResponseDTO::from);
    }

    public ContactMessageResponseDTO findMineById(UUID uuid, String userEmail) {
        return ContactMessageResponseDTO.from(getOwned(uuid, userEmail));
    }

    /**
     * Corrección del propio mensaje. Se rechaza una vez que administración lo leyó, para que lo
     * que se responda siga correspondiendo a lo que se preguntó.
     */
    @Transactional
    public ContactMessageResponseDTO update(UUID uuid, String userEmail, ContactMessageRequestDTO request) {
        ContactMessage message = getOwned(uuid, userEmail);
        if (message.isRead()) {
            throw BusinessRuleException.messageAlreadyRead(uuid);
        }
        message.setCategory(request.category());
        message.setSubject(request.subject().strip());
        message.setBody(request.body().strip());
        message.setUpdatedAt(OffsetDateTime.now());
        return ContactMessageResponseDTO.from(message);
    }

    /**
     * Bandeja de administración. Listar no marca nada como leído: sólo abrir un mensaje lo hace.
     *
     * @param read  {@code true} sólo leídos, {@code false} sólo pendientes, {@code null} todos
     * @param email parte del correo del autor
     */
    public PageResponseDTO<ContactMessageResponseDTO> findAll(@Nullable ContactCategory category,
                                                              @Nullable Boolean read,
                                                              @Nullable String email,
                                                              Pageable pageable) {
        Specification<ContactMessage> spec = ContactMessageSpecifications.withAuthor()
                .and(ContactMessageSpecifications.hasCategory(category))
                .and(ContactMessageSpecifications.isRead(read))
                .and(ContactMessageSpecifications.emailContains(email));
        return PageResponseDTO.from(contactMessageRepository.findAll(spec, pageable),
                ContactMessageResponseDTO::from);
    }

    /**
     * Apertura de un mensaje por administración. La primera lo marca como leído, lo que impide que
     * el autor lo siga corrigiendo.
     */
    @Transactional
    public ContactMessageResponseDTO open(UUID uuid) {
        ContactMessage message = getMessage(uuid);
        if (!message.isRead()) {
            message.setReadAt(OffsetDateTime.now());
        }
        return ContactMessageResponseDTO.from(message);
    }

    @Transactional
    public void delete(UUID uuid) {
        contactMessageRepository.delete(getMessage(uuid));
    }

    /**
     * Un mensaje ajeno responde igual que uno inexistente, para no filtrar su existencia.
     */
    private ContactMessage getOwned(UUID uuid, String userEmail) {
        ContactMessage message = getMessage(uuid);
        if (!message.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw NotFoundException.of(RESOURCE, uuid);
        }
        return message;
    }

    private ContactMessage getMessage(UUID uuid) {
        return contactMessageRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of(RESOURCE, uuid));
    }
}
