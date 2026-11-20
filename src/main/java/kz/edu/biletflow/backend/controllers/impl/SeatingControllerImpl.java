package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.SeatingController;
import kz.edu.biletflow.backend.dtos.AssignSectionRequest;
import kz.edu.biletflow.backend.dtos.BlockSeatsRequest;
import kz.edu.biletflow.backend.dtos.SeatMapResponse;
import kz.edu.biletflow.backend.dtos.SectionSeatsResponse;
import kz.edu.biletflow.backend.dtos.SetSeatingModeRequest;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.SeatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SeatingControllerImpl implements SeatingController {
    private final SeatingService seatingService;

    @Override
    public ResponseEntity<SeatMapResponse> getSeating(UserPrincipal currentUser, Long eventId) {
        if (currentUser == null) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        return ResponseEntity.ok(seatingService.getSeating(eventId, currentUser.getId(), currentUser.hasRole("PLATFORM_ADMIN")));
    }

    @Override
    public ResponseEntity<SeatMapResponse> setSeatingMode(UserPrincipal currentUser, Long eventId, SetSeatingModeRequest request) {
        return ResponseEntity.ok(seatingService.setSeatingMode(currentUser.getId(), eventId, request.getMode()));
    }

    @Override
    public ResponseEntity<SeatMapResponse> assignSection(UserPrincipal currentUser, Long eventId, Long sectionId,
                                                         AssignSectionRequest request) {
        return ResponseEntity.ok(seatingService.assignSection(currentUser.getId(), eventId, sectionId, request.getTicketTypeId()));
    }

    @Override
    public ResponseEntity<SeatMapResponse> setSeatsBlocked(UserPrincipal currentUser, Long eventId, BlockSeatsRequest request) {
        return ResponseEntity.ok(seatingService.setSeatsBlocked(currentUser.getId(), eventId, request.getSeatIds(),
                request.getBlocked()));
    }

    @Override
    public ResponseEntity<SeatMapResponse> getSeatMap(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(seatingService.getSeatMap(eventId, requesterId(currentUser), isPlatformAdmin(currentUser)));
    }

    @Override
    public ResponseEntity<SectionSeatsResponse> getSectionSeats(UserPrincipal currentUser, Long eventId, Long sectionId) {
        return ResponseEntity.ok(seatingService.getSectionSeats(eventId, sectionId, requesterId(currentUser),
                isPlatformAdmin(currentUser)));
    }

    private static Long requesterId(UserPrincipal currentUser) {
        return currentUser != null ? currentUser.getId() : null;
    }

    private static boolean isPlatformAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.hasRole("PLATFORM_ADMIN");
    }
}
