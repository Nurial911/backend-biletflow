package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.dtos.TicketTypeResponse;
import kz.edu.biletflow.backend.dtos.UpdateTicketTypeRequest;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.TicketStatus;
import kz.edu.biletflow.backend.entities.TicketType;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.repositories.TicketTypeRepository;
import kz.edu.biletflow.backend.services.TicketTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;

    @Override
    @Transactional
    public TicketTypeResponse create(Long organizerId, Long eventId, CreateTicketTypeRequest request) {
        Event event = ownedEvent(organizerId, eventId);
        validateWindow(request.getSalesStartTime(), request.getSalesEndTime());
        TicketType ticketType = new TicketType();
        ticketType.setEvent(event);
        ticketType.setName(request.getName());
        ticketType.setDescription(request.getDescription());
        ticketType.setPrice(request.getPrice());
        ticketType.setQuantity(request.getQuantity());
        ticketType.setSalesStartTime(request.getSalesStartTime());
        ticketType.setSalesEndTime(request.getSalesEndTime());
        ticketType.setLimitPerOrder(request.getLimitPerOrder());
        ticketType.setHidden(request.isHidden());
        return toResponse(ticketTypeRepository.save(ticketType));
    }

    @Override
    @Transactional
    public TicketTypeResponse update(Long organizerId, Long ticketTypeId, UpdateTicketTypeRequest request) {
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found with id: " + ticketTypeId));
        ownedEvent(organizerId, ticketType.getEvent().getId());
        if (request.getName() != null) ticketType.setName(request.getName());
        if (request.getDescription() != null) ticketType.setDescription(request.getDescription());
        if (request.getPrice() != null) ticketType.setPrice(request.getPrice());
        if (request.getQuantity() != null) {
            long issued = ticketRepository.countByTicketTypeIdAndStatusIn(ticketTypeId,
                    List.of(TicketStatus.VALID, TicketStatus.CHECKED_IN));
            if (request.getQuantity() < issued) {
                throw new BusinessRuleViolationException("Quantity cannot be lower than tickets already issued");
            }
            ticketType.setQuantity(request.getQuantity());
        }
        if (request.getSalesStartTime() != null) ticketType.setSalesStartTime(request.getSalesStartTime());
        if (request.getSalesEndTime() != null) ticketType.setSalesEndTime(request.getSalesEndTime());
        if (request.getLimitPerOrder() != null) ticketType.setLimitPerOrder(request.getLimitPerOrder());
        if (request.getHidden() != null) ticketType.setHidden(request.getHidden());
        validateWindow(ticketType.getSalesStartTime(), ticketType.getSalesEndTime());
        return toResponse(ticketTypeRepository.save(ticketType));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketTypeResponse> list(Long eventId, Pageable pageable) {
        return ticketTypeRepository.findAllByEventId(eventId, pageable).map(this::toResponse);
    }

    private Event ownedEvent(Long organizerId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenOperationException("You are not authorized to manage this event");
        }
        return event;
    }

    private void validateWindow(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && !start.isBefore(end)) {
            throw new BusinessRuleViolationException("Sales start time must be before sales end time");
        }
    }

    private TicketTypeResponse toResponse(TicketType ticketType) {
        TicketTypeResponse response = new TicketTypeResponse();
        response.setId(ticketType.getId());
        response.setEventId(ticketType.getEvent().getId());
        response.setName(ticketType.getName());
        response.setDescription(ticketType.getDescription());
        response.setPrice(ticketType.getPrice());
        response.setQuantity(ticketType.getQuantity());
        response.setSalesStartTime(ticketType.getSalesStartTime());
        response.setSalesEndTime(ticketType.getSalesEndTime());
        response.setLimitPerOrder(ticketType.getLimitPerOrder());
        response.setHidden(ticketType.isHidden());
        return response;
    }
}
