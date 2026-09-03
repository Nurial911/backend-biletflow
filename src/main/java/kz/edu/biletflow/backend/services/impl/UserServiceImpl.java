package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse registerUser(RegisterUserRequest request) {
        return null;
    }
}
