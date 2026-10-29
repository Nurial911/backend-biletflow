package kz.edu.biletflow.backend.security;

import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BadCredentialsException;
import kz.edu.biletflow.backend.exception.DuplicateResourceException;
import kz.edu.biletflow.backend.mappers.UserMapper;
import kz.edu.biletflow.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public JwtAuthResponse login(LoginRequest request) {
        // emails are stored lowercase (seeder, register), so match case-insensitively
        User user = userRepository.findByEmailIgnoreCase(request.getEmail()).orElse(null);
        // TEMP-DEBUG (login investigation): remove once the frontend login works. Never log the password.
        log.info("TEMP-DEBUG login attempt email='{}' userFound={}", request.getEmail(), user != null);
        if (user == null) {
            throw new BadCredentialsException("Incorrect email or password.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // TEMP-DEBUG: length only, to spot an extra space/newline added by the form
            log.info("TEMP-DEBUG login password mismatch for '{}' (password length {})",
                    user.getEmail(), request.getPassword().length());
            throw new BadCredentialsException("Incorrect email or password.");
        }

        String token = jwtService.generateToken(user);
        return new JwtAuthResponse(token, userMapper.toDto(user));
    }

    @Transactional
    public JwtAuthResponse registerUser(RegisterUserRequest request) {
        // check if email is already registered
        // store lowercase so case-insensitive login can never match two accounts
        request.setEmail(request.getEmail().trim().toLowerCase());
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new DuplicateResourceException("Email is already registered");
        }

        User newUser = userMapper.toEntity(request);

        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setRole(User.Role.ATTENDEE);

        User savedUser = userRepository.save(newUser);

        UserResponse userDto = userMapper.toDto(savedUser);
        String token = jwtService.generateToken(savedUser);

        return new JwtAuthResponse(token, userDto);
    }
}
