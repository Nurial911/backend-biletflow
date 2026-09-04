package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateVenueRequest;
import kz.edu.biletflow.backend.dtos.VenueResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("api/venues")
public interface VenueController {
    @PostMapping
    ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody CreateVenueRequest request);

    @GetMapping
    ResponseEntity<Page<VenueResponse>> getAllVenues(Pageable pageable);
}
