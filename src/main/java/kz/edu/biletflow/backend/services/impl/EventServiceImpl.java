package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.EventMapper;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.OrderRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.repositories.VenueRepository;
import kz.edu.biletflow.backend.services.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    private static final Long ANONYMOUS_ID = -1L;

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final EventMapper eventMapper;
    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;

    @Transactional
    @Override
    public EventResponse createEvent(Long organizerId, CreateEventRequest eventRequest) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizer not found with id: " + organizerId));
        if (organizer.getRole() != User.Role.ORGANIZER) {
            throw new ForbiddenOperationException("User is not an organizer");
        }
        Venue venue = venueRepository.findById(eventRequest.getVenueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + eventRequest.getVenueId()));

        validateEventTiming(eventRequest.getRegistrationOpeningTime(),
                eventRequest.getRegistrationClosingTime(),
                eventRequest.getStartTime(),
                eventRequest.getEndTime());

        Event event = eventMapper.toEntity(eventRequest);
        event.setOrganizer(organizer);
        event.setVenue(venue);
        return eventMapper.toDto(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    @Override
    public EventResponse getEventById(Long id, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
        boolean isOwner = requesterId != null && event.getOrganizer().getId().equals(requesterId);
        boolean isPubliclyVisible = event.getStatus() == EventStatus.PUBLISHED
                && event.getVisibilityStatus() != VisibilityStatus.PRIVATE;
        if (isPlatformAdmin) {
            return eventMapper.toDto(event);
        }
        if (!isOwner && !isPubliclyVisible) {
            throw new ResourceNotFoundException("Event not found with id: " + id);
        }

        return eventMapper.toDto(event);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<EventResponse> getAllEvents(Pageable pageable, Long requesterId, boolean isPlatformAdmin) {
        if (isPlatformAdmin) {
            return eventRepository.findAll(pageable).map(eventMapper::toDto);
        } else {
            return eventRepository.findVisibleEvents(requesterId != null ? requesterId : ANONYMOUS_ID, pageable)
                    .map(eventMapper::toDto);
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Page<EventResponse> getEventsByOrganizer(Long organizerId, Pageable pageable, Long requesterId, boolean isPlatformAdmin) {
        if (isPlatformAdmin) {
            return eventRepository.findAllByOrganizerId(organizerId, pageable).map(eventMapper::toDto);
        } else {
            return eventRepository.findByOrganizerVisibleTo(organizerId,
                            requesterId != null ? requesterId : ANONYMOUS_ID, pageable)
                    .map(eventMapper::toDto);
        }
    }

    @Transactional
    @Override
    public EventResponse updateEvent(Long organizerId, Long eventId, UpdateEventRequest request) {
        Event event = getOwnedEvent(organizerId, eventId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("A cancelled event cannot be edited.");
        }
        eventMapper.updateEvent(request, event);

        if (request.getVenueId() != null){
            Venue venue = venueRepository.findById(request.getVenueId())
                    .orElseThrow(() -> new BusinessRuleViolationException("Venue not found with id: " + request.getVenueId()));
            event.setVenue(venue);
        }

        validateEventTiming(event.getRegistrationOpeningTime(),
                event.getRegistrationClosingTime(),
                event.getStartTime(),
                event.getEndTime());

        return eventMapper.toDto(eventRepository.save(event));
    }

    @Transactional
    @Override
    public EventResponse publishEvent(Long organizerId, Long eventId) {
        Event event = getOwnedEvent(organizerId, eventId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("A cancelled event cannot be published.");
        }
        if (event.getStatus() == EventStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("Event is already published.");
        }

        event.setStatus(EventStatus.PUBLISHED);
        return eventMapper.toDto(eventRepository.save(event));
    }

    @Transactional
    @Override
    public EventResponse unpublishEvent(Long organizerId, Long eventId) {
        Event event = getOwnedEvent(organizerId, eventId);

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("Only a published event can be unpublished");
        }

        event.setStatus(EventStatus.DRAFT);
        return eventMapper.toDto(eventRepository.save(event));
    }

    @Transactional
    @Override
    public EventResponse cancelEvent(Long organizerId, Long eventId) {
        Event event = getOwnedEvent(organizerId, eventId);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Event is already cancelled");
        }

        event.setStatus(EventStatus.CANCELLED);
        Event saved = eventRepository.save(event);

        // Cancelled tickets must not admit anyone; unpaid orders can no longer be paid.
        // PAID orders keep their status until refunds exist (they need a refund, not a silent change).
        ticketRepository.updateStatusByEventId(eventId, TicketStatus.VALID, TicketStatus.CANCELLED);
        orderRepository.updateStatusByEventId(eventId, OrderStatus.PENDING, OrderStatus.CANCELLED);

        return eventMapper.toDto(saved);
    }

    @Transactional
    @Override
    public EventResponse duplicateEvent(Long organizerId, Long eventId) {
        Event originalEvent = getOwnedEvent(organizerId, eventId);

        Event copy = new Event();

        copy.setTitle(originalEvent.getTitle());
        copy.setDescription(originalEvent.getDescription());
        copy.setCapacity(originalEvent.getCapacity());
        copy.setStartTime(originalEvent.getStartTime());
        copy.setEndTime(originalEvent.getEndTime());
        copy.setRegistrationOpeningTime(originalEvent.getRegistrationOpeningTime());
        copy.setRegistrationClosingTime(originalEvent.getRegistrationClosingTime());
        copy.setVenue(originalEvent.getVenue());
        copy.setVisibilityStatus(originalEvent.getVisibilityStatus());
        copy.setStatus(EventStatus.DRAFT);
        copy.setOrganizer(originalEvent.getOrganizer());
        copy.setCategory(originalEvent.getCategory());

        return eventMapper.toDto(eventRepository.save(copy));
    }

    private void validateEventTiming(LocalDateTime opening, LocalDateTime closing,
                                     LocalDateTime start, LocalDateTime end) {
        if (opening != null && closing != null && !opening.isBefore(closing)) {
            throw new BusinessRuleViolationException("Registration opening time must be before the closing time");
        }
        if (closing != null && start != null && closing.isAfter(start)) {
            throw new BusinessRuleViolationException("Registration must close before the event starts");
        }
        if (end != null && start != null && !end.isAfter(start)) {
            throw new BusinessRuleViolationException("Event end time must be after the start time");
        }
    }

    private Event getOwnedEvent(Long organizerId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenOperationException("You are not authorized to modify this event");
        }
        return event;
    }

}
