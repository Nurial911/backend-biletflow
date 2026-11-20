package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.CreateVenueRequest;
import kz.edu.biletflow.backend.dtos.VenueResponse;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.Venue;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.VenueMapper;
import kz.edu.biletflow.backend.repositories.VenueRepository;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {
    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;
    private final SeatingLayouts seatingLayouts;
    private final AuditService auditService;

    @Override
    @Transactional
    public VenueResponse createVenue(CreateVenueRequest createVenueRequest) {
        Venue venue = venueMapper.toEntity(createVenueRequest);
        String layoutKey = blankToNull(createVenueRequest.getSeatingLayout());
        venue.setSeatingLayout(null);
        if (layoutKey != null) {
            // A venue with a seat map holds exactly what the map holds.
            SeatingLayouts.Layout layout = findLayout(layoutKey);
            if (venue.getCapacity() != null && venue.getCapacity() != layout.capacity()) {
                throw new BusinessRuleViolationException("The '" + layout.name() + "' seat map holds " + layout.capacity()
                        + " people: leave capacity out or set it to " + layout.capacity());
            }
            venue.setSeatingLayout(layout.key());
            venue.setCapacity(layout.capacity());
        } else if (venue.getCapacity() == null) {
            throw new BusinessRuleViolationException("Venue capacity is required");
        }
        Venue savedVenue = venueRepository.save(venue);
        return venueMapper.toDto(savedVenue);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VenueResponse> getAllVenues(Pageable pageable) {
        Page<Venue> venues = venueRepository.findAll(pageable);
        return venues.map(venueMapper::toDto); // venue -> venueMapper.toDto(venue)
    }

    @Override
    @Transactional
    public VenueResponse setSeatingLayout(Long adminId, Long venueId, @Nullable String layoutKey) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + venueId));
        String key = blankToNull(layoutKey);
        String old = venue.getSeatingLayout();
        if (Objects.equals(old, key)) {
            return venueMapper.toDto(venue);
        }
        if (key != null) {
            SeatingLayouts.Layout layout = findLayout(key);
            venue.setSeatingLayout(layout.key());
            venue.setCapacity(layout.capacity());
        } else {
            venue.setSeatingLayout(null); // the capacity stays: it is still how many people the venue holds
        }
        Venue saved = venueRepository.save(venue);
        auditService.record(adminId, AuditAction.VENUE_SEATING_LAYOUT_CHANGED, venueId, null,
                "Seat map of venue '" + venue.getName() + "' changed from " + (old == null ? "none" : "'" + old + "'")
                        + " to " + (key == null ? "none" : "'" + key + "'"));
        return venueMapper.toDto(saved);
    }

    private SeatingLayouts.Layout findLayout(String key) {
        return seatingLayouts.find(key).orElseThrow(() -> new BusinessRuleViolationException(
                "Unknown seating layout '" + key + "'. See GET /api/venues/seating-layouts"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
