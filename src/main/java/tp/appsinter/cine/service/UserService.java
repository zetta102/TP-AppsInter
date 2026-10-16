package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.BusinessRuleException;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.ChangePasswordRequestDTO;
import tp.appsinter.cine.model.dto.request.CreateUserRequestDTO;
import tp.appsinter.cine.model.dto.request.UpdateProfileRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateUserResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.entities.User;
import tp.appsinter.cine.model.utils.Role;
import tp.appsinter.cine.repository.ReservationRepository;
import tp.appsinter.cine.repository.UserRepository;
import tp.appsinter.cine.repository.UserSpecifications;
import tp.appsinter.cine.security.RefreshTokenService;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CreateUserResponseDTO createUser(CreateUserRequestDTO request) {
        return createUser(request, Role.CUSTOMER);
    }

    @Transactional
    public CreateUserResponseDTO createUser(CreateUserRequestDTO request, Role role) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ConflictException.emailTaken(request.email());
        }
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email().toLowerCase())
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .build();
        return CreateUserResponseDTO.from(userRepository.save(user));
    }

    /**
     * Búsqueda de administración sobre las cuentas activas. Todos los filtros son opcionales y se
     * combinan con «y».
     *
     * @param email parte del correo
     * @param name  parte del nombre, del apellido o del nombre completo
     */
    public PageResponseDTO<CreateUserResponseDTO> findAll(@Nullable Role role,
                                                          @Nullable String email,
                                                          @Nullable String name,
                                                          Pageable pageable) {
        Specification<User> spec = UserSpecifications.active()
                .and(UserSpecifications.hasRole(role))
                .and(UserSpecifications.emailContains(email))
                .and(UserSpecifications.nameContains(name));
        Page<User> page = userRepository.findAll(spec, pageable);
        return PageResponseDTO.from(page, CreateUserResponseDTO::from);
    }

    /**
     * Corrección de los datos propios.
     */
    @Transactional
    public CreateUserResponseDTO updateProfile(String email, UpdateProfileRequestDTO request) {
        User user = getByEmail(email);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        return CreateUserResponseDTO.from(user);
    }

    /**
     * Cambio de la contraseña propia.
     *
     * <p>Exige la contraseña vigente porque un token robado no debería alcanzar para apropiarse de
     * la cuenta, y revoca el resto de las sesiones para echar a quien haya entrado con la anterior.
     *
     * @throws BusinessRuleException si la contraseña vigente no coincide, con código
     *                               {@code WRONG_PASSWORD}. No es un {@code 401}: la sesión sigue
     *                               siendo válida y el cliente no debe cerrarla
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequestDTO request) {
        User user = getByEmail(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw BusinessRuleException.wrongPassword();
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllExcept(user, request.refreshToken());
    }

    /**
     * Cambio de rol de otra cuenta.
     *
     * <p>Se rechaza que un administrador se degrade a sí mismo y que la operación deje el sistema
     * sin ningún administrador: en ambos casos nadie podría volver a repararlo desde la API.
     */
    @Transactional
    public CreateUserResponseDTO updateRole(UUID uuid, Role role, String callerEmail) {
        User user = getUser(uuid);
        if (user.isDeleted()) {
            throw NotFoundException.of("User", uuid);
        }
        if (user.getRole() == role) {
            return CreateUserResponseDTO.from(user);
        }
        if (user.getRole() == Role.ADMIN) {
            if (user.getEmail().equalsIgnoreCase(callerEmail)) {
                throw BusinessRuleException.lastAdmin("An administrator cannot change their own role");
            }
            if (userRepository.countByRoleAndDeletedAtIsNull(Role.ADMIN) <= 1) {
                throw BusinessRuleException.lastAdmin("The system must keep at least one administrator");
            }
        }
        user.setRole(role);
        return CreateUserResponseDTO.from(user);
    }

    /**
     * Baja lógica de una cuenta.
     *
     * <p>No se borra la fila: sus reservas son historial de ventas. Se revocan las sesiones y se
     * bloquea el inicio de sesión. Se rechaza mientras el usuario tenga reservas vigentes, porque
     * esas butacas siguen vendidas y necesitan un titular al que avisar.
     */
    @Transactional
    public void softDelete(UUID uuid, String callerEmail) {
        User user = getUser(uuid);
        if (user.isDeleted()) {
            return;
        }
        if (user.getEmail().equalsIgnoreCase(callerEmail)) {
            throw BusinessRuleException.lastAdmin("An administrator cannot delete their own account");
        }
        if (user.getRole() == Role.ADMIN && userRepository.countByRoleAndDeletedAtIsNull(Role.ADMIN) <= 1) {
            throw BusinessRuleException.lastAdmin("The system must keep at least one administrator");
        }
        if (reservationRepository.existsActiveForUser(uuid, OffsetDateTime.now())) {
            throw ConflictException.resourceInUse(
                    "User %s still holds reservations for upcoming screenings".formatted(uuid),
                    "User", "Reservation");
        }
        user.setDeletedAt(OffsetDateTime.now());
        refreshTokenService.revokeAll(user);
    }

    public CreateUserResponseDTO findById(UUID uuid) {
        User user = getUser(uuid);
        if (user.isDeleted()) {
            throw NotFoundException.of("User", uuid);
        }
        return CreateUserResponseDTO.from(user);
    }

    public User getByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new NotFoundException("User %s not found".formatted(email), "User"));
    }

    /**
     * Identificador de un usuario a partir de su correo, o {@code null} si no existe.
     *
     * <p>Devuelve nulo en lugar de fallar porque quien lo usa está resolviendo quién mira una
     * pantalla pública: un correo que ya no corresponde a nadie sólo significa que no hay a quién
     * tratar de forma especial.
     */
    public UUID findUuidByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).map(User::getUuid).orElse(null);
    }

    User getUser(UUID uuid) {
        return userRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("User", uuid));
    }
}
