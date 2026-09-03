package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.CreateVenueRequest;
import kz.edu.biletflow.backend.dtos.VenueResponse;
import kz.edu.biletflow.backend.entities.Venue;
import kz.edu.biletflow.backend.mappers.VenueMapper;
import kz.edu.biletflow.backend.repositories.VenueRepository;
import kz.edu.biletflow.backend.services.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {
    private final VenueRepository venueRepository;
    private final VenueMapper venueMapper;

    @Override
    public VenueResponse createVenue(CreateVenueRequest createVenueRequest) {
        Venue venue = venueMapper.toEntity(createVenueRequest);
        Venue savedVenue = venueRepository.save(venue);
        return venueMapper.toDto(savedVenue);
    }

    @Override
    public Page<VenueResponse> getAllVenues(Pageable pageable) {
        Page<Venue> venues = venueRepository.findAll(pageable);
        return venues.map(venueMapper::toDto); // venue -> venueMapper.toDto(venue)
    }
}
