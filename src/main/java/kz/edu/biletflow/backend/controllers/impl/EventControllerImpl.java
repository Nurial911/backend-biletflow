package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.EventController;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.PosterImage;
import kz.edu.biletflow.backend.dtos.SuspendEventRequest;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class EventControllerImpl implements EventController {
    private final EventService eventService;

    @Override
    public ResponseEntity<EventResponse> createEvent(CreateEventRequest request, UserPrincipal currentUser) {
        Long organizerId = currentUser.getId();

        EventResponse createdEvent = eventService.createEvent(organizerId, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/events/{id}")
                .buildAndExpand(createdEvent.getId())
                .toUri();
        return ResponseEntity.created(location).body(createdEvent);
    }

    @Override
    public ResponseEntity<EventResponse> getEventById(Long id, UserPrincipal currentUser) {
        Long requesterId = currentUser != null ? currentUser.getId() : null;
        boolean isPlatformAdmin = currentUser != null
                && currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(eventService.getEventById(id, requesterId, isPlatformAdmin));
    }

    @Override
    public ResponseEntity<Page<EventResponse>> getAllEvents(Pageable pageable, UserPrincipal currentUser) {
        boolean isPlatformAdmin = currentUser != null
                && currentUser.hasRole("PLATFORM_ADMIN");
        Long requesterId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(eventService.getAllEvents(pageable, requesterId, isPlatformAdmin));
    }

    @Override
    public ResponseEntity<Page<EventResponse>> getEventsByOrganizer(Long organizerId, Pageable pageable, UserPrincipal currentUser) {
        Long requesterId = currentUser != null ? currentUser.getId() : null;
        boolean isPlatformAdmin = currentUser != null
                && currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(eventService.getEventsByOrganizer(organizerId, pageable, requesterId, isPlatformAdmin));
    }

    @Override
    public ResponseEntity<EventResponse> updateEvent(UserPrincipal currentUser, Long eventId, UpdateEventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(currentUser.getId(), eventId, request));
    }

    @Override
    public ResponseEntity<EventResponse> publishEvent(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(eventService.publishEvent(currentUser.getId(), eventId));
    }

    @Override
    public ResponseEntity<EventResponse> unpublishEvent(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(eventService.unpublishEvent(currentUser.getId(), eventId));
    }

    @Override
    public ResponseEntity<EventResponse> cancelEvent(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(eventService.cancelEvent(currentUser.getId(), eventId));
    }

    @Override
    public ResponseEntity<EventResponse> duplicateEvent(UserPrincipal currentUser, Long eventId) {
        EventResponse duplicatedEvent = eventService.duplicateEvent(currentUser.getId(), eventId);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/events/{id}")
                .buildAndExpand(duplicatedEvent.getId())
                .toUri();
        return ResponseEntity.created(location).body(duplicatedEvent);
    }

    @Override
    public ResponseEntity<EventResponse> suspendEvent(Long eventId, SuspendEventRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(eventService.suspendEvent(eventId, reason));
    }

    @Override
    public ResponseEntity<EventResponse> unsuspendEvent(Long eventId) {
        return ResponseEntity.ok(eventService.unsuspendEvent(eventId));
    }

    @Override
    public ResponseEntity<EventResponse> uploadPoster(UserPrincipal currentUser, Long eventId, MultipartFile file) {
        return ResponseEntity.ok(eventService.uploadPoster(currentUser.getId(), eventId, file));
    }

    @Override
    public ResponseEntity<Void> deletePoster(UserPrincipal currentUser, Long eventId) {
        eventService.deletePoster(currentUser.getId(), eventId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<byte[]> getPoster(Long eventId, UserPrincipal currentUser) {
        Long requesterId = currentUser != null ? currentUser.getId() : null;
        boolean isPlatformAdmin = currentUser != null
                && currentUser.hasRole("PLATFORM_ADMIN");
        PosterImage poster = eventService.getPoster(eventId, requesterId, isPlatformAdmin);
        // posterUrl carries ?v=<key>, which changes on every upload, so long caching is safe.
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(30));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(poster.contentType()))
                .cacheControl(poster.isPublic() ? cache.cachePublic() : cache.cachePrivate())
                .body(poster.bytes());
    }
}
