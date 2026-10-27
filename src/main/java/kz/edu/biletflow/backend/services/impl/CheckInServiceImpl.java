package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.EventMapper;
import kz.edu.biletflow.backend.mappers.TicketMapper;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.CheckInService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final CheckInRecordRepository checkInRecordRepository;
    private final UserRepository userRepository;
    private final EventMapper eventMapper;
    private final TicketMapper ticketMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getAssignedEvents(Long staffId, Pageable pageable) {
        return staffAssignmentRepository.findAllByUserId(staffId, pageable)
                .map(assignment -> eventMapper.toDto(assignment.getEvent()));
    }

    @Override
    @Transactional
    public CheckInResponse scan(Long eventId, String code, Long staffId, boolean isPlatformAdmin) {
        Event event = getCheckInEvent(eventId, staffId, isPlatformAdmin);

        // Locked so two scanners reading the same ticket at once can't both admit it.
        Optional<Ticket> found = ticketRepository.findByCodeForUpdate(code.trim());
        if (found.isEmpty()) {
            return new CheckInResponse(CheckInResult.INVALID, "Unknown ticket code", null, null);
        }
        Ticket ticket = found.get();
        if (!ticket.getEvent().getId().equals(event.getId())) {
            // Don't show details of a ticket that belongs to another organizer's event.
            return new CheckInResponse(CheckInResult.WRONG_EVENT, "This ticket is for a different event", null, null);
        }
        if (event.getStatus() == EventStatus.CANCELLED || ticket.getStatus() == TicketStatus.CANCELLED) {
            return new CheckInResponse(CheckInResult.CANCELLED, "This ticket is cancelled",
                    ticket.getUsedAt(), ticketMapper.toDto(ticket));
        }
        if (ticket.getStatus() == TicketStatus.USED) {
            return new CheckInResponse(CheckInResult.ALREADY_USED, "This ticket has already been used",
                    ticket.getUsedAt(), ticketMapper.toDto(ticket));
        }

        ticket.setStatus(TicketStatus.USED);
        ticket.setUsedAt(LocalDateTime.now());
        record(ticket, staffId, CheckInAction.CHECK_IN);
        return new CheckInResponse(CheckInResult.CHECKED_IN, "Checked in",
                ticket.getUsedAt(), ticketMapper.toDto(ticket));
    }

    @Override
    @Transactional
    public TicketResponse undo(Long eventId, Long ticketId, Long staffId, boolean isPlatformAdmin) {
        Event event = getCheckInEvent(eventId, staffId, isPlatformAdmin);
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .filter(t -> t.getEvent().getId().equals(event.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));
        if (ticket.getStatus() != TicketStatus.USED) {
            throw new BusinessRuleViolationException("Only a checked-in ticket can be undone, this ticket is " + ticket.getStatus());
        }

        ticket.setStatus(TicketStatus.VALID);
        ticket.setUsedAt(null);
        record(ticket, staffId, CheckInAction.UNDO);
        return ticketMapper.toDto(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> searchAttendees(Long eventId, String query, Long staffId, boolean isPlatformAdmin, Pageable pageable) {
        getCheckInEvent(eventId, staffId, isPlatformAdmin);
        String q = query == null ? "" : query.trim();
        return ticketRepository.searchByEvent(eventId, q, pageable).map(ticketMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInStatsResponse getStats(Long eventId, Long staffId, boolean isPlatformAdmin) {
        getCheckInEvent(eventId, staffId, isPlatformAdmin);
        long checkedIn = ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.USED);
        long notCheckedIn = ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.VALID);
        return new CheckInStatsResponse(eventId, checkedIn + notCheckedIn, checkedIn, notCheckedIn);
    }

    // helpers //

    private void record(Ticket ticket, Long staffId, CheckInAction action) {
        CheckInRecord entry = new CheckInRecord();
        entry.setTicket(ticket);
        entry.setEvent(ticket.getEvent());
        entry.setPerformedBy(userRepository.getReferenceById(staffId));
        entry.setAction(action);
        checkInRecordRepository.save(entry);
    }

    // Check-in is allowed for staff assigned to the event, its organizer and platform admins.
    // Everyone else gets 404 so event ids can't be probed through the scanner endpoints.
    private Event getCheckInEvent(Long eventId, Long userId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        boolean isOwner = event.getOrganizer().getId().equals(userId);
        if (!isPlatformAdmin && !isOwner && !staffAssignmentRepository.existsByEventIdAndUserId(eventId, userId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        return event;
    }
}
