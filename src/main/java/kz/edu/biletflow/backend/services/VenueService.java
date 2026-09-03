package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CreateVenueRequest;
import kz.edu.biletflow.backend.dtos.VenueResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VenueService {
    VenueResponse createVenue(CreateVenueRequest createVenueRequest);

    Page<VenueResponse> getAllVenues(Pageable pageable);
}
