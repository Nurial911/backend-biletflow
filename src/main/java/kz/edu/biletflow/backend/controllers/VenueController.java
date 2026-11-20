package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateVenueRequest;
import kz.edu.biletflow.backend.dtos.SeatingLayoutResponse;
import kz.edu.biletflow.backend.dtos.SetVenueLayoutRequest;
import kz.edu.biletflow.backend.dtos.VenueResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RequestMapping("api/venues")
public interface VenueController {
    @PreAuthorize("hasAnyRole('ORGANIZER','PLATFORM_ADMIN')")
    @PostMapping
    ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest request);

    @GetMapping
    ResponseEntity<Page<VenueResponse>> getAllVenues(Pageable pageable);

    // Public: the predefined seat maps a venue can have (SRS 4.3.1).
    @GetMapping("/seating-layouts")
    ResponseEntity<List<SeatingLayoutResponse>> getSeatingLayouts();

    // Venues have no owner, so only a platform admin changes a seat map after creation.
    // Events that already switched to assigned seating keep their own copy.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/{venueId}/seating-layout")
    ResponseEntity<VenueResponse> setSeatingLayout(@AuthenticationPrincipal UserPrincipal currentUser,
                                                   @PathVariable Long venueId,
                                                   @Valid @RequestBody SetVenueLayoutRequest request);
}
