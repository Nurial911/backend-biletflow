package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.DuplicateResourceException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.UserMapper;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.OrganizerProfileRepository;
import kz.edu.biletflow.backend.repositories.StaffAssignmentRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.AccountEmailService;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final EventRepository eventRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AccountEmailService accountEmailService;

    @Override
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found"));
        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserCredentials(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found"));
        if (request.getEmail() != null && request.getEmail().trim().isEmpty()) {
            request.setEmail(null); // blank means "no change"; the mapper only ignores null
        }
        if (request.getEmail() != null
                && !request.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email " + request.getEmail() + " is already registered");
        }

        boolean emailChanged = request.getEmail() != null && !request.getEmail().equals(user.getEmail());

        userMapper.updateUser(request, user);

        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (emailChanged) {
            user.setVerificationStatus(false); // the new address hasn't been confirmed
        }

        User savedUser = userRepository.save(user);
        if (emailChanged) {
            accountEmailService.sendVerification(savedUser);
        }
        return userMapper.toDto(savedUser);
    }

    @Override
    @Transactional
    public UserResponse changeRole(Long adminId, Long userId, User.Role role) {
        if (adminId.equals(userId)) {
            // Prevents an admin from locking themselves (and possibly the platform) out of admin access.
            throw new BusinessRuleViolationException("You cannot change your own role");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));
        User.Role oldRole = user.getRole();
        if (oldRole == role) {
            return userMapper.toDto(user);
        }

        // ORGANIZER comes with an organizer profile; it is granted through POST /api/organizers/profile.
        if (role == User.Role.ORGANIZER && !organizerProfileRepository.existsByUserId(userId)) {
            throw new BusinessRuleViolationException(
                    "User has no organizer profile; they become an organizer by creating one");
        }
        // Their events would be left without anyone able to manage them.
        if (oldRole == User.Role.ORGANIZER && eventRepository.existsByOrganizerId(userId)) {
            throw new BusinessRuleViolationException("An organizer who owns events cannot be given another role");
        }
        // Check-in requires the EVENT_ADMIN role, so the assignments would be dead rows.
        if (oldRole == User.Role.EVENT_ADMIN) {
            staffAssignmentRepository.deleteAllByUserId(userId);
        }

        user.setRole(role);
        User saved = userRepository.save(user);
        auditService.record(adminId, AuditAction.USER_ROLE_CHANGED, userId, null,
                saved.getEmail() + " role changed from " + oldRole + " to " + role);
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long actorId, Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found"));
        auditService.record(actorId, AuditAction.USER_DELETED, id, null, user.getEmail() + " account deleted");
        userRepository.delete(user);
    }

    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        Page<User> usersPage = userRepository.findAll(pageable);
        return usersPage.map(userMapper::toDto);
    }
}
