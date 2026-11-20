package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.SectionKind;
import lombok.Data;

import java.util.List;

@Data
public class SeatingLayoutResponse {
    private String key;
    private String name;
    private String description;
    private int capacity;          // seats + standing places
    private int seatCount;
    private int standingCapacity;
    private List<Section> sections;

    @Data
    public static class Section {
        private String key;
        private String name;
        private SectionKind kind;
        private int capacity;
    }
}
