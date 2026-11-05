package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.PosterImage;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
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
    private final PosterStorage posterStorage;

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
        applyVenueCapacity(event);
        return eventMapper.toDto(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    @Override
    public EventResponse getEventById(Long id, @Nullable Long requesterId, boolean isPlatformAdmin) {
        return eventMapper.toDto(getVisibleEvent(id, requesterId, isPlatformAdmin));
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
        ensureNotSuspended(event);

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("A cancelled event cannot be edited.");
        }
        eventMapper.updateEvent(request, event);

        if (request.getVenueId() != null){
            Venue venue = venueRepository.findById(request.getVenueId())
                    .orElseThrow(() -> new BusinessRuleViolationException("Venue not found with id: " + request.getVenueId()));
            event.setVenue(venue);
        }
        // Checked on the merged entity, so changing only the venue or only the capacity is still validated.
        applyVenueCapacity(event);

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
        ensureNotSuspended(event);

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
        ensureNotSuspended(event);

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
        ensureNotSuspended(originalEvent); // otherwise a copy would sidestep the suspension

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
        if (originalEvent.getPosterKey() != null) {
            // Own file, so removing one event's poster doesn't break the other.
            String copiedKey = posterStorage.copy(originalEvent.getPosterKey());
            deleteFileOnRollback(copiedKey);
            copy.setPosterKey(copiedKey);
        }

        return eventMapper.toDto(eventRepository.save(copy));
    }

    @Transactional
    @Override
    public EventResponse suspendEvent(Long eventId, @Nullable String reason) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (event.isSuspended()) {
            throw new BusinessRuleViolationException("Event is already suspended");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("A cancelled event cannot be suspended");
        }

        event.setSuspended(true);
        event.setSuspendedAt(LocalDateTime.now());
        event.setSuspensionReason(reason != null && !reason.isBlank() ? reason.trim() : null);
        Event saved = eventRepository.save(event);

        // Stop sales: unpaid reservations are released. PAID orders and their tickets stay valid —
        // a suspension is reversible, so it must not destroy anything a refund would be needed for.
        orderRepository.updateStatusByEventId(eventId, OrderStatus.PENDING, OrderStatus.CANCELLED);

        return eventMapper.toDto(saved);
    }

    @Transactional
    @Override
    public EventResponse unsuspendEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (!event.isSuspended()) {
            throw new BusinessRuleViolationException("Event is not suspended");
        }

        event.setSuspended(false);
        event.setSuspendedAt(null);
        event.setSuspensionReason(null);
        return eventMapper.toDto(eventRepository.save(event));
    }

    @Transactional
    @Override
    public EventResponse uploadPoster(Long organizerId, Long eventId, MultipartFile file) {
        Event event = getOwnedEvent(organizerId, eventId);
        ensureNotSuspended(event);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("A cancelled event cannot be edited.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded poster", e);
        }
        PosterFormat format = PosterFormat.detect(bytes)
                .orElseThrow(() -> new BusinessRuleViolationException("Poster must be a JPEG, PNG or WebP image"));

        String oldKey = event.getPosterKey();
        String newKey = posterStorage.save(bytes, format);
        deleteFileOnRollback(newKey);
        if (oldKey != null) {
            deleteFileAfterCommit(oldKey);
        }

        event.setPosterKey(newKey);
        return eventMapper.toDto(eventRepository.save(event));
    }

    @Transactional
    @Override
    public void deletePoster(Long organizerId, Long eventId) {
        Event event = getOwnedEvent(organizerId, eventId);
        ensureNotSuspended(event);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("A cancelled event cannot be edited.");
        }
        if (event.getPosterKey() == null) {
            throw new ResourceNotFoundException("Event has no poster");
        }

        deleteFileAfterCommit(event.getPosterKey());
        event.setPosterKey(null);
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    @Override
    public PosterImage getPoster(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = getVisibleEvent(eventId, requesterId, isPlatformAdmin);
        if (event.getPosterKey() == null) {
            throw new ResourceNotFoundException("Event has no poster");
        }
        boolean isPublic = event.getStatus() == EventStatus.PUBLISHED
                && event.getVisibilityStatus() == VisibilityStatus.PUBLIC
                && !event.isSuspended();
        return new PosterImage(posterStorage.load(event.getPosterKey()),
                PosterFormat.fromKey(event.getPosterKey()).getContentType(),
                isPublic);
    }

    // Files are not part of the DB transaction: the old poster goes only once the new key is committed,
    // and a freshly written file is removed again if the transaction rolls back.
    private void deleteFileAfterCommit(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                posterStorage.delete(key);
            }
        });
    }

    private void deleteFileOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    posterStorage.delete(key);
                }
            }
        });
    }

    // Hidden-existence rule: events the requester may not see answer 404, not 403.
    private Event getVisibleEvent(Long id, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
        if (isPlatformAdmin) {
            return event;
        }
        boolean isOwner = requesterId != null && event.getOrganizer().getId().equals(requesterId);
        boolean isPubliclyVisible = event.getStatus() == EventStatus.PUBLISHED
                && event.getVisibilityStatus() != VisibilityStatus.PRIVATE
                && !event.isSuspended();
        if (!isOwner && !isPubliclyVisible) {
            throw new ResourceNotFoundException("Event not found with id: " + id);
        }
        return event;
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

    private void applyVenueCapacity(Event event) {
        Integer venueCapacity = event.getVenue().getCapacity();
        if (venueCapacity == null) {
            return; // legacy venue without a capacity limit
        }
        if (event.getCapacity() == null) {
            event.setCapacity(venueCapacity);
        } else if (event.getCapacity() > venueCapacity) {
            throw new BusinessRuleViolationException("Event capacity (" + event.getCapacity()
                    + ") exceeds venue capacity (" + venueCapacity + ")");
        }
    }

    // A suspended event is frozen for its organizer until an admin lifts it; only cancel stays allowed.
    private void ensureNotSuspended(Event event) {
        if (event.isSuspended()) {
            throw new BusinessRuleViolationException("This event is suspended by a platform administrator");
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
