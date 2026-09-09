package kz.edu.biletflow.backend.services.impl;

import com.sun.jdi.request.EventRequest;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.entities.Venue;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.EventMapper;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.repositories.VenueRepository;
import kz.edu.biletflow.backend.services.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final EventMapper eventMapper;

    @Override
    public EventResponse createEvent(Long organizerId, CreateEventRequest eventRequest) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizer not found with id: " + organizerId));
        if (organizer.getRole() != User.Role.ORGANIZER) {
            throw new ForbiddenOperationException("User is not an organizer");
        }
        Venue venue = venueRepository.findById(eventRequest.getVenueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + eventRequest.getVenueId()));

        validateEventTiming(eventRequest);

        Event event = eventMapper.toEntity(eventRequest);
        event.setOrganizer(organizer);
        event.setVenue(venue);
        return eventMapper.toDto(eventRepository.save(event));
    }

    private void validateEventTiming(CreateEventRequest request) {

        LocalDateTime opening = request.getRegistrationOpeningTime();
        LocalDateTime closing = request.getRegistrationClosingTime();

        if (opening != null && closing != null && !opening.isBefore(closing)) {
            throw new BusinessRuleViolationException("Registration opening time must be before the closing time");
        }

        if (closing != null && closing.isAfter(request.getStartTime())) {
            throw new BusinessRuleViolationException("Registration must close before the event starts");
        }
    }
}
