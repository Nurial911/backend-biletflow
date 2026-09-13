package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.AnalyticsResponse;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.TicketStatus;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    @Override
    @Transactional(readOnly = true)
    public AnalyticsResponse eventAnalytics(Long organizerId, Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (!event.getOrganizer().getId().equals(organizerId)) throw new ForbiddenOperationException("Not event owner");
        long issued = ticketRepository.countByEventId(eventId);
        long checkedIn = ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.CHECKED_IN);
        long invalidated = ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.CANCELLED)
                + ticketRepository.countByEventIdAndStatus(eventId, TicketStatus.REFUNDED);
        AnalyticsResponse response = new AnalyticsResponse();
        response.setCapacity(event.getCapacity() == null ? 0 : event.getCapacity());
        response.setTicketsIssued(issued);
        response.setCheckedIn(checkedIn);
        response.setCancelledOrRefunded(invalidated);
        response.setSales(BigDecimal.ZERO);
        return response;
    }
}
