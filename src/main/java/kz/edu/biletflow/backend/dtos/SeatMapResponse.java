package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.SeatingMode;
import lombok.Data;

import java.util.List;

// The seat map of an event: its sections with prices and availability. Seats come per section
// (GET /api/events/{id}/seat-map/sections/{sectionId}), so a stadium doesn't arrive in one response.
@Data
public class SeatMapResponse {
    private Long eventId;
    private SeatingMode seatingMode;
    private String layoutKey;      // null when the venue has no seat map
    private String layoutName;
    private Integer width;         // map size, in the layout's units
    private Integer height;
    private Area stage;            // null when the layout has none
    private Integer capacity;
    private List<SeatSectionResponse> sections;

    @Data
    public static class Area {
        private String label;
        private int x;
        private int y;
        private int width;
        private int height;
    }
}
