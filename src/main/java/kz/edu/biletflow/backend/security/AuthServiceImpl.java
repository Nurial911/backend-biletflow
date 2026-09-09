package kz.edu.biletflow.backend.security;

import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BadCredentialsException;
import kz.edu.biletflow.backend.exception.DuplicateResourceException;
import kz.edu.biletflow.backend.mappers.UserMapper;
import kz.edu.biletflow.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public JwtAuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Incorrect email or password."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Incorrect email or password.");
        }

        String token = jwtService.generateToken(user);
        return new JwtAuthResponse(token, userMapper.toDto(user));
    }

    @Transactional
    public JwtAuthResponse registerUser(RegisterUserRequest request) {
        // check if email is already registered
        if (userRepository.existsByEmail(request.getEmail())) {
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
