package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.AssignStaffRequest;
import kz.edu.biletflow.backend.dtos.StaffResponse;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.StaffAssignment;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.DuplicateResourceException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.StaffAssignmentMapper;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.StaffAssignmentRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final StaffAssignmentMapper staffAssignmentMapper;

    @Override
    @Transactional
    public StaffResponse assignStaff(Long eventId, Long requesterId, boolean isPlatformAdmin, AssignStaffRequest request) {
        Event event = getManagedEvent(eventId, requesterId, isPlatformAdmin);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Staff cannot be assigned to a cancelled event");
        }

        User user = userRepository.findByEmail(request.getEmail().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));
        if (user.getRole() != User.Role.EVENT_ADMIN) {
            throw new BusinessRuleViolationException("Only users with the EVENT_ADMIN role can be assigned as check-in staff");
        }
        if (staffAssignmentRepository.existsByEventIdAndUserId(eventId, user.getId())) {
            throw new DuplicateResourceException("This user is already assigned to the event");
        }

        StaffAssignment assignment = new StaffAssignment();
        assignment.setEvent(event);
        assignment.setUser(user);
        return staffAssignmentMapper.toDto(staffAssignmentRepository.save(assignment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffResponse> getStaff(Long eventId, Long requesterId, boolean isPlatformAdmin) {
        getManagedEvent(eventId, requesterId, isPlatformAdmin);
        return staffAssignmentRepository.findAllByEventIdOrderByIdAsc(eventId).stream()
                .map(staffAssignmentMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void removeStaff(Long eventId, Long userId, Long requesterId, boolean isPlatformAdmin) {
        getManagedEvent(eventId, requesterId, isPlatformAdmin);
        StaffAssignment assignment = staffAssignmentRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " is not assigned to this event"));
        staffAssignmentRepository.delete(assignment);
    }

    // Only the event's organizer (or a platform admin) manages its staff. Everyone else gets 404,
    // also because GET /api/events/** is public and must not reveal the event or its staff list.
    private Event getManagedEvent(Long eventId, Long requesterId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        boolean isOwner = requesterId != null && event.getOrganizer().getId().equals(requesterId);
        if (!isOwner && !isPlatformAdmin) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        return event;
    }
}
