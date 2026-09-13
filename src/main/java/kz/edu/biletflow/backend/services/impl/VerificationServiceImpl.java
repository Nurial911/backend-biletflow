package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.TicketResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.CheckInRecordRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.QrCodeService;
import kz.edu.biletflow.backend.services.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {
    private final TicketRepository ticketRepository;
    private final CheckInRecordRepository checkInRecordRepository;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;

    @Override
    @Transactional(readOnly = true)
    public TicketResponse validate(String ticketIdentifier, String qrPayload) {
        Ticket ticket = findTicket(ticketIdentifier);
        verifyPayload(ticket, qrPayload);
        return toResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse checkIn(Long adminId, String ticketIdentifier, String qrPayload) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found"));
        Ticket ticket = findTicket(ticketIdentifier);
        verifyPayload(ticket, qrPayload);
        if (ticket.getStatus() != TicketStatus.VALID) {
            throw new BusinessRuleViolationException("Ticket is not valid for check-in");
        }
        ticket.setStatus(TicketStatus.CHECKED_IN);
        CheckInRecord record = new CheckInRecord();
        record.setTicket(ticket);
        record.setEventAdmin(admin);
        record.setStatus(CheckInStatus.CHECKED_IN);
        checkInRecordRepository.save(record);
        return toResponse(ticketRepository.save(ticket));
    }

    @Override
    @Transactional
    public TicketResponse undoCheckIn(Long adminId, String ticketIdentifier) {
        Ticket ticket = findTicket(ticketIdentifier);
        if (ticket.getStatus() != TicketStatus.CHECKED_IN) {
            throw new BusinessRuleViolationException("Ticket is not checked in");
        }
        CheckInRecord record = checkInRecordRepository.findByTicketIdAndStatus(ticket.getId(), CheckInStatus.CHECKED_IN)
                .orElseThrow(() -> new BusinessRuleViolationException("Check-in record not found"));
        record.setStatus(CheckInStatus.REVERSED);
        ticket.setStatus(TicketStatus.VALID);
        checkInRecordRepository.save(record);
        return toResponse(ticketRepository.save(ticket));
    }

    private Ticket findTicket(String identifier) {
        return ticketRepository.findByTicketIdentifier(identifier)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    private void verifyPayload(Ticket ticket, String payload) {
        if (!qrCodeService.isValid(payload, ticket.getTicketIdentifier())) {
            throw new BusinessRuleViolationException("Invalid ticket QR payload");
        }
        if (ticket.getStatus() == TicketStatus.CANCELLED || ticket.getStatus() == TicketStatus.REFUNDED) {
            throw new BusinessRuleViolationException("Ticket is no longer valid");
        }
    }

    private TicketResponse toResponse(Ticket ticket) {
        TicketResponse response = new TicketResponse();
        response.setId(ticket.getId());
        response.setTicketIdentifier(ticket.getTicketIdentifier());
        response.setEventId(ticket.getEvent().getId());
        response.setTicketTypeId(ticket.getTicketType().getId());
        response.setStatus(ticket.getStatus());
        return response;
    }
}
