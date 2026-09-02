package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
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

    private final UserService userService;

    @GetMapping
    public PageResponseDTO<CreateUserResponseDTO> findAll(
            @RequestParam(required = false) Role role,
            @PageableDefault(size = 20, sort = "email") Pageable pageable) {
        return userService.findAll(role, pageable);
    }

    @GetMapping("/me")
    public CreateUserResponseDTO currentUser(Authentication authentication) {
        return CreateUserResponseDTO.from(userService.getByEmail(authentication.getName()));
    }

    @GetMapping("/{uuid}")
    public CreateUserResponseDTO findById(@PathVariable UUID uuid) {
        return userService.findById(uuid);
    }
}
