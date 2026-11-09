package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.TicketResponse;
import kz.edu.biletflow.backend.entities.Ticket;
import kz.edu.biletflow.backend.entities.TicketStatus;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.TicketMapper;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {
    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final QrCodeGenerator qrCodeGenerator;
    private final TicketPdfGenerator ticketPdfGenerator;

    @Override
    @Transactional(readOnly = true)
    public Page<TicketResponse> getMyTickets(Long userId, Pageable pageable) {
        return ticketRepository.findAllByOwnerId(userId, pageable).map(ticketMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long ticketId, Long requesterId, boolean isPlatformAdmin) {
        return ticketMapper.toDto(getVisibleTicket(ticketId, requesterId, isPlatformAdmin));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getTicketQr(Long ticketId, Long requesterId, boolean isPlatformAdmin) {
        Ticket ticket = getVisibleTicket(ticketId, requesterId, isPlatformAdmin);
        // The QR holds only the opaque code; check-in looks everything else up on the server.
        return qrCodeGenerator.toPng(ticket.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getTicketPdf(Long ticketId, Long requesterId, boolean isPlatformAdmin) {
        Ticket ticket = getVisibleTicket(ticketId, requesterId, isPlatformAdmin);
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new BusinessRuleViolationException("This ticket is cancelled and can no longer be printed");
        }
        return ticketPdfGenerator.generate(List.of(ticket));
    }

    private Ticket getVisibleTicket(Long ticketId, Long requesterId, boolean isPlatformAdmin) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with id: " + ticketId));
        if (!isPlatformAdmin && !ticket.getOwner().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Ticket not found with id: " + ticketId);
        }
        return ticket;
    }
}
