package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.TicketResponse;
import kz.edu.biletflow.backend.entities.Ticket;
import kz.edu.biletflow.backend.entities.TicketStatus;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {
    private final TicketRepository ticketRepository;

    @Override
    @Transactional
    public TicketResponse cancel(Long userId, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));
        if (!ticket.getAttendee().getUser().getId().equals(userId)) {
            throw new ForbiddenOperationException("You are not authorized to cancel this ticket");
        }
        if (ticket.getStatus() != TicketStatus.VALID) {
            throw new BusinessRuleViolationException("Only valid tickets can be cancelled");
        }
        ticket.setStatus(TicketStatus.CANCELLED);
        return toResponse(ticketRepository.save(ticket));
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
