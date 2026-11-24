package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.Predicate;
import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.NotificationType;
import kz.edu.biletflow.backend.entities.OrderStatus;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ErrorCode;
import kz.edu.biletflow.backend.exception.DuplicateResourceException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.i18n.Text;
import kz.edu.biletflow.backend.mappers.UserMapper;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.OrderRepository;
import kz.edu.biletflow.backend.repositories.OrganizerProfileRepository;
import kz.edu.biletflow.backend.repositories.StaffAssignmentRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.AccountEmailService;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.NotificationService;
import kz.edu.biletflow.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


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
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

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
            throw new DuplicateResourceException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email " + request.getEmail() + " is already registered");
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
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(@Nullable String q, @Nullable User.Role role, @Nullable Boolean suspended,
                                          Pageable pageable) {
        String text = SearchText.normalize(q);
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (text != null) {
                String pattern = SearchText.containsPattern(text);
                Predicate match = cb.or(
                        cb.like(cb.lower(root.get("email")), pattern, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("fullName")), pattern, SearchText.ESCAPE));
                Long id = SearchText.asId(text);
                if (id != null) {
                    match = cb.or(match, cb.equal(root.get("id"), id));
                }
                predicates.add(match);
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (suspended != null) {
                predicates.add(cb.equal(root.get("suspended"), suspended));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return userRepository.findAll(spec, pageable).map(userMapper::toDto);
    }

    @Override
    @Transactional
    public UserResponse suspendUser(Long adminId, Long userId, @Nullable String reason) {
        if (adminId.equals(userId)) {
            throw new BusinessRuleViolationException("You cannot suspend your own account");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));
        if (user.isSuspended()) {
            throw new BusinessRuleViolationException("User is already suspended");
        }
        // Admins can't lock each other out by accident; demote first if it is really needed.
        if (user.getRole() == User.Role.PLATFORM_ADMIN) {
            throw new BusinessRuleViolationException("A platform admin cannot be suspended; change their role first");
        }

        String trimmedReason = reason != null && !reason.isBlank() ? reason.trim() : null;
        user.setSuspended(true);
        user.setSuspendedAt(LocalDateTime.now());
        user.setSuspensionReason(trimmedReason);
        User saved = userRepository.save(user);

        // Unpaid reservations go back on sale. Bought tickets stay valid: the suspension is reversible,
        // and refunding them is a separate decision for the organizer.
        orderRepository.updateStatusByUserId(userId, OrderStatus.PENDING, OrderStatus.CANCELLED);

        auditService.record(adminId, AuditAction.USER_SUSPENDED, userId, null,
                saved.getEmail() + " suspended" + (trimmedReason != null ? ": " + trimmedReason : ""));
        notificationService.notifyUser(userId, NotificationType.ACCOUNT_SUSPENDED,
                Text.of("account.suspended.title"),
                Text.of("account.suspended.body")
                        .andIf(trimmedReason != null, "common.reason", trimmedReason)
                        .and("account.suspended.footer"),
                null);
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public UserResponse unsuspendUser(Long adminId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));
        if (!user.isSuspended()) {
            throw new BusinessRuleViolationException("User is not suspended");
        }
        user.setSuspended(false);
        user.setSuspendedAt(null);
        user.setSuspensionReason(null);
        User saved = userRepository.save(user);

        auditService.record(adminId, AuditAction.USER_UNSUSPENDED, userId, null, saved.getEmail() + " unsuspended");
        notificationService.notifyUser(userId, NotificationType.ACCOUNT_REINSTATED,
                Text.of("account.reinstated.title"),
                Text.of("account.reinstated.body"), null);
        return userMapper.toDto(saved);
    }}
