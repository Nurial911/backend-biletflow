package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/events")
public interface EventController {

    @PostMapping("/{organizerId}")
    ResponseEntity<EventResponse> createEvent(@PathVariable Long organizerId,
                                              @Valid @RequestBody CreateEventRequest request);
}
