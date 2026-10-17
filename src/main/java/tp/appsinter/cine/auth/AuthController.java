package tp.appsinter.cine.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import tp.appsinter.cine.auth.dto.AuthResponseDTO;
import tp.appsinter.cine.auth.dto.LoginRequestDTO;
import tp.appsinter.cine.auth.dto.RefreshTokenRequestDTO;
import tp.appsinter.cine.user.User;
import tp.appsinter.cine.user.UserService;
import tp.appsinter.cine.user.dto.CreateUserRequestDTO;
import tp.appsinter.cine.user.dto.UserResponseDTO;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody CreateUserRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        return tokensFor(userService.getByEmail(request.email()));
    }

    @PostMapping("/refresh")
    public AuthResponseDTO refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        var rotated = refreshTokenService.rotate(request.refreshToken());
        JwtService.IssuedToken issued = jwtService.issue(rotated.user());
        return AuthResponseDTO.bearer(issued.token(), issued.expiresAt(),
                rotated.refreshToken().token(), rotated.refreshToken().expiresAt());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequestDTO request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthResponseDTO tokensFor(User user) {
        JwtService.IssuedToken issued = jwtService.issue(user);
        var refresh = refreshTokenService.issue(user);
        return AuthResponseDTO.bearer(issued.token(), issued.expiresAt(), refresh.token(), refresh.expiresAt());
    }
}
