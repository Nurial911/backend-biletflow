package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.CalendarController;
import kz.edu.biletflow.backend.dtos.CalendarLinksResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.CalendarService;
import kz.edu.biletflow.backend.services.impl.EventCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CalendarControllerImpl implements CalendarController {
    private final CalendarService calendarService;

    @Override
    public ResponseEntity<byte[]> getEventCalendar(Long eventId, UserPrincipal currentUser) {
        CalendarService.CalendarFile file = calendarService.getEventCalendar(eventId, requesterId(currentUser),
                isPlatformAdmin(currentUser));
        // Always revalidated: the file changes whenever the event does.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(EventCalendar.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .cacheControl(CacheControl.noCache())
                .body(file.content());
    }

    @Override
    public ResponseEntity<CalendarLinksResponse> getCalendarLinks(Long eventId, UserPrincipal currentUser) {
        return ResponseEntity.ok(calendarService.getCalendarLinks(eventId, requesterId(currentUser),
                isPlatformAdmin(currentUser)));
    }

    private static Long requesterId(UserPrincipal currentUser) {
        return currentUser != null ? currentUser.getId() : null;
    }

    private static boolean isPlatformAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.hasRole("PLATFORM_ADMIN");
    }
}
