package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.VenueController;
import kz.edu.biletflow.backend.dtos.CreateVenueRequest;
import kz.edu.biletflow.backend.dtos.VenueResponse;
import kz.edu.biletflow.backend.services.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class VenueControllerImpl implements VenueController {
    private final VenueService venueService;

    @Override
    public ResponseEntity<VenueResponse> createVenue(CreateVenueRequest request) {
        VenueResponse createdVenue = venueService.createVenue(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/venues/{id}")
                .buildAndExpand(createdVenue.getId())
                .toUri();
        return ResponseEntity.created(location).body(createdVenue);
    }

    @Override
    public ResponseEntity<Page<VenueResponse>> getAllVenues(Pageable pageable) {
        Page<VenueResponse> venues = venueService.getAllVenues(pageable);
        return ResponseEntity.ok(venues);
    }
}
