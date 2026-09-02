package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.appsinter.cine.exception.ConflictException;
import tp.appsinter.cine.exception.NotFoundException;
import tp.appsinter.cine.model.dto.request.CreateUserRequestDTO;
import tp.appsinter.cine.model.dto.response.CreateUserResponseDTO;
import tp.appsinter.cine.model.dto.response.PageResponseDTO;
import tp.appsinter.cine.model.entities.User;
import tp.appsinter.cine.model.utils.Role;
import tp.appsinter.cine.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CreateUserResponseDTO createUser(CreateUserRequestDTO request) {
        return createUser(request, Role.CUSTOMER);
    }

    @Transactional
    public CreateUserResponseDTO createUser(CreateUserRequestDTO request, Role role) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email %s is already registered".formatted(request.email()));
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

    public PageResponseDTO<CreateUserResponseDTO> findAll(Role role, Pageable pageable) {
        Page<User> page = role == null
                ? userRepository.findAll(pageable)
                : userRepository.findByRole(role, pageable);
        return PageResponseDTO.from(page, CreateUserResponseDTO::from);
    }

    public CreateUserResponseDTO findById(UUID uuid) {
        return CreateUserResponseDTO.from(getUser(uuid));
    }

    public User getByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("User %s not found".formatted(email)));
    }

    User getUser(UUID uuid) {
        return userRepository.findById(uuid)
                .orElseThrow(() -> NotFoundException.of("User", uuid));
    }
}
