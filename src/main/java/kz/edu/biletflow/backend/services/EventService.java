package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.PosterImage;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface EventService {
    EventResponse createEvent(Long organizerId, CreateEventRequest eventRequest);

    EventResponse getEventById(Long id, @Nullable Long requesterId, boolean isPlatformAdmin);

    Page<EventResponse> getAllEvents(Pageable pageable, Long requesterId, boolean isPlatformAdmin);

    Page<EventResponse> getEventsByOrganizer(Long organizerId, Pageable pageable, Long requesterId, boolean isPlatformAdmin);

    EventResponse updateEvent(Long organizerId, Long eventId, UpdateEventRequest request);

    EventResponse publishEvent(Long organizerId, Long eventId);

    EventResponse unpublishEvent(Long organizerId, Long eventId);

    EventResponse cancelEvent(Long organizerId, Long eventId);

    EventResponse duplicateEvent(Long organizerId, Long eventId);

    EventResponse suspendEvent(Long adminId, Long eventId, @Nullable String reason);

    EventResponse unsuspendEvent(Long adminId, Long eventId);

    EventResponse uploadPoster(Long organizerId, Long eventId, MultipartFile file);

    void deletePoster(Long organizerId, Long eventId);

    PosterImage getPoster(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin);

}
