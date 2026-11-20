package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.util.List;

@Data
public class SectionSeatsResponse {
    private SeatSectionResponse section;
    private List<Seat> seats;      // empty for a standing section

    @Data
    public static class Seat {
        private Long id;
        private String row;
        private int number;
        private int x;             // seat centre on the map
        private int y;
        private boolean accessible;
        private SeatStatus status;
    }
}
