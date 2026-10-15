package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.model.dto.request.ChangePasswordRequestDTO;
import tp.appsinter.cine.model.dto.request.UpdateProfileRequestDTO;
import tp.appsinter.cine.model.dto.request.UpdateRoleRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateUserResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.utils.Role;
import tp.appsinter.cine.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of("email", "firstName", "lastName", "role");

    private final UserService userService;

    @GetMapping
    public PageResponseDTO<CreateUserResponseDTO> findAll(
            @RequestParam(required = false) Role role,
            @ParameterObject Pageable pageable) {
        return userService.findAll(role, SORTABLE.apply(pageable));
    }

    @GetMapping("/me")
    public CreateUserResponseDTO currentUser(Authentication authentication) {
        return CreateUserResponseDTO.from(userService.getByEmail(authentication.getName()));
    }

    /**
     * Corrección de los datos propios.
     */
    @PatchMapping("/me")
    public CreateUserResponseDTO updateProfile(Authentication authentication,
                                               @Valid @RequestBody UpdateProfileRequestDTO request) {
        return userService.updateProfile(authentication.getName(), request);
    }

    /**
     * Cambio de la contraseña propia. Revoca el resto de las sesiones del usuario.
     */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication,
                               @Valid @RequestBody ChangePasswordRequestDTO request) {
        userService.changePassword(authentication.getName(), request);
    }

    /**
     * Cambio de rol de otra cuenta. Sólo administración.
     */
    @PatchMapping("/{uuid}/role")
    public CreateUserResponseDTO updateRole(Authentication authentication,
                                            @PathVariable UUID uuid,
                                            @Valid @RequestBody UpdateRoleRequestDTO request) {
        return userService.updateRole(uuid, request.role(), authentication.getName());
    }

    /**
     * Baja lógica de una cuenta. Sólo administración.
     */
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable UUID uuid) {
        userService.softDelete(uuid, authentication.getName());
    }

    @GetMapping("/{uuid}")
    public CreateUserResponseDTO findById(@PathVariable UUID uuid) {
        return userService.findById(uuid);
    }
}
