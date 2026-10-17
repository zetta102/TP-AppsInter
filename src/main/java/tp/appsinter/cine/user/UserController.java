package tp.appsinter.cine.user;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.shared.dto.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;
import tp.appsinter.cine.user.dto.ChangePasswordRequestDTO;
import tp.appsinter.cine.user.dto.UpdateProfileRequestDTO;
import tp.appsinter.cine.user.dto.UpdateRoleRequestDTO;
import tp.appsinter.cine.user.dto.UserResponseDTO;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @GetMapping
    public PageResponseDTO<UserResponseDTO> findAll(
            @RequestParam(required = false) Role role,
            @Parameter(description = "Part of the e-mail, case-insensitive")
            @RequestParam(required = false) String email,
            @Parameter(description = "Part of the first name, last name or full name, case-insensitive")
            @RequestParam(required = false) String name,
            @ParameterObject @AllowedSort({"email", "firstName", "lastName", "role"}) Pageable pageable) {
        return userService.findAll(role, email, name, pageable);
    }

    @GetMapping("/me")
    public UserResponseDTO currentUser(Authentication authentication) {
        return UserResponseDTO.from(userService.getByEmail(authentication.getName()));
    }

    /**
     * Corrección de los datos propios.
     */
    @PatchMapping("/me")
    public UserResponseDTO updateProfile(Authentication authentication,
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
    public UserResponseDTO updateRole(Authentication authentication,
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
    public UserResponseDTO findById(@PathVariable UUID uuid) {
        return userService.findById(uuid);
    }
}
