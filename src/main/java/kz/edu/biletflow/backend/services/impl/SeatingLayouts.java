package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.entities.SectionKind;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The predefined seat maps (SRS 4.3.1: "at least one predefined venue layout", no layout editor).
 * Each file in resources/seating describes one layout compactly (row ranges such as "A-L" with a seat count);
 * this class validates them at startup, so a broken file stops the app instead of a sale, and expands them
 * into seats with map coordinates.
 * Events copy a layout when they switch to assigned seating, so editing a file only affects later events.
 */
@Component
public class SeatingLayouts {
    // Distance between neighbouring seats and rows, in map units.
    static final int SEAT_PITCH = 10;

    private static final Pattern LAYOUT_KEY = Pattern.compile("[a-z0-9-]{1,50}");
    private static final Pattern SECTION_KEY = Pattern.compile("[A-Z0-9_]{1,20}");
    private static final Pattern NUMBER_RANGE = Pattern.compile("(\\d{1,3})-(\\d{1,3})");
    private static final Pattern LETTER_RANGE = Pattern.compile("([A-Z])-([A-Z])");
    private static final int MAX_LABEL_LENGTH = 10;
    private static final int MAX_NAME_LENGTH = 100;

    public enum RowDirection {DOWN, UP, LEFT, RIGHT}

    public record Area(String label, int x, int y, int width, int height) {
    }

    public record Seat(String row, int number, int x, int y, boolean accessible) {
    }

    public record Section(String key, String name, SectionKind kind, int capacity,
                          int x, int y, int width, int height, List<Seat> seats) {
    }

    public record Layout(String key, String name, String description, int width, int height,
                         @Nullable Area stage, List<Section> sections) {
        public int capacity() {
            return sections.stream().mapToInt(Section::capacity).sum();
        }

        public int seatCount() {
            return sections.stream().filter(s -> s.kind() == SectionKind.SEATED).mapToInt(Section::capacity).sum();
        }

        public int standingCapacity() {
            return capacity() - seatCount();
        }
    }

    // File format.
    record LayoutFile(String key, String name, String description, Integer width, Integer height,
                      AreaFile stage, List<SectionFile> sections) {
    }

    record AreaFile(String label, Integer x, Integer y, Integer width, Integer height) {
    }

    record SectionFile(String key, String name, SectionKind kind, Integer capacity, Integer x, Integer y,
                       Integer width, Integer height, RowDirection rowDirection, List<RowFile> rows) {
    }

    record RowFile(String labels, Integer seats, List<Integer> accessibleSeats) {
    }

    private final Map<String, Layout> layouts;

    public SeatingLayouts() {
        this("classpath:seating/*.json");
    }

    SeatingLayouts(String locationPattern) {
        JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        Map<String, Layout> loaded = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
            List<Resource> sorted = new ArrayList<>(List.of(resources));
            sorted.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));
            for (Resource resource : sorted) {
                Layout layout;
                try (InputStream in = resource.getInputStream()) {
                    layout = build(mapper.readValue(in, LayoutFile.class));
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Invalid seating layout " + resource.getFilename() + ": " + e.getMessage(), e);
                }
                if (loaded.putIfAbsent(layout.key(), layout) != null) {
                    throw new IllegalStateException("Seating layout key '" + layout.key() + "' is used twice");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the seating layouts", e);
        }
        this.layouts = Map.copyOf(loaded);
    }

    public Optional<Layout> find(String key) {
        return Optional.ofNullable(key).map(layouts::get);
    }

    public Collection<Layout> all() {
        return layouts.values().stream().sorted(Comparator.comparing(Layout::key)).toList();
    }

    // building //

    private static Layout build(LayoutFile file) {
        require(file.key() != null && LAYOUT_KEY.matcher(file.key()).matches(), "key must match " + LAYOUT_KEY);
        requireName(file.name(), "name");
        require(positive(file.width()) && positive(file.height()), "width and height must be positive");
        require(file.sections() != null && !file.sections().isEmpty(), "a layout needs at least one section");

        Area stage = null;
        if (file.stage() != null) {
            AreaFile s = file.stage();
            require(s.x() != null && s.y() != null && positive(s.width()) && positive(s.height()), "stage needs x, y, width and height");
            stage = new Area(s.label() != null ? s.label() : "Stage", s.x(), s.y(), s.width(), s.height());
        }

        Set<String> keys = new HashSet<>();
        List<Section> sections = new ArrayList<>();
        for (SectionFile section : file.sections()) {
            require(section.key() != null && SECTION_KEY.matcher(section.key()).matches(),
                    "section key must match " + SECTION_KEY);
            require(keys.add(section.key()), "section key " + section.key() + " is used twice");
            requireName(section.name(), "section " + section.key() + " name");
            require(section.kind() != null, "section " + section.key() + " needs a kind");
            require(section.x() != null && section.y() != null, "section " + section.key() + " needs x and y");
            sections.add(section.kind() == SectionKind.STANDING ? standing(section) : seated(section));
        }
        return new Layout(file.key(), file.name(), file.description(), file.width(), file.height(), stage, List.copyOf(sections));
    }

    private static Section standing(SectionFile section) {
        require(positive(section.capacity()), "standing section " + section.key() + " needs a positive capacity");
        require(positive(section.width()) && positive(section.height()), "standing section " + section.key() + " needs width and height");
        require(section.rows() == null || section.rows().isEmpty(), "standing section " + section.key() + " can't have rows");
        return new Section(section.key(), section.name(), SectionKind.STANDING, section.capacity(),
                section.x(), section.y(), section.width(), section.height(), List.of());
    }

    private static Section seated(SectionFile section) {
        require(section.rows() != null && !section.rows().isEmpty(), "seated section " + section.key() + " needs rows");
        require(section.capacity() == null, "seated section " + section.key() + ": capacity is the number of seats, leave it out");

        // Expand "A-L" into its rows, keeping every row's seat count and accessible places.
        record Row(String label, int seats, Set<Integer> accessible) {
        }
        List<Row> rows = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        for (RowFile row : section.rows()) {
            require(positive(row.seats()), "rows of section " + section.key() + " need a positive seat count");
            Set<Integer> accessible = new HashSet<>(row.accessibleSeats() != null ? row.accessibleSeats() : List.of());
            for (int seat : accessible) {
                require(seat >= 1 && seat <= row.seats(), "accessible seat " + seat + " doesn't exist in section " + section.key());
            }
            for (String label : expand(row.labels())) {
                require(labels.add(label), "row " + label + " is listed twice in section " + section.key());
                rows.add(new Row(label, row.seats(), accessible));
            }
        }

        RowDirection direction = section.rowDirection() != null ? section.rowDirection() : RowDirection.DOWN;
        int widest = rows.stream().mapToInt(Row::seats).max().orElseThrow();
        int along = widest * SEAT_PITCH;       // length of the longest row
        int across = rows.size() * SEAT_PITCH; // depth of the section
        boolean horizontalRows = direction == RowDirection.DOWN || direction == RowDirection.UP;

        List<Seat> seats = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int depth = switch (direction) {
                case DOWN, RIGHT -> r;
                case UP, LEFT -> rows.size() - 1 - r; // row 1 nearest the field, rows grow away from it
            };
            int indent = (widest - row.seats()) * SEAT_PITCH / 2; // shorter rows are centred
            for (int s = 0; s < row.seats(); s++) {
                int a = indent + s * SEAT_PITCH + SEAT_PITCH / 2;
                int d = depth * SEAT_PITCH + SEAT_PITCH / 2;
                int x = section.x() + (horizontalRows ? a : d);
                int y = section.y() + (horizontalRows ? d : a);
                seats.add(new Seat(row.label(), s + 1, x, y, row.accessible().contains(s + 1)));
            }
        }
        return new Section(section.key(), section.name(), SectionKind.SEATED, seats.size(), section.x(), section.y(),
                horizontalRows ? along : across, horizontalRows ? across : along, List.copyOf(seats));
    }

    // "A-L" -> A..L, "1-25" -> 1..25, anything else is a single row label.
    static List<String> expand(String labels) {
        require(labels != null && !labels.isBlank(), "a row needs labels");
        String text = labels.trim();
        Matcher numbers = NUMBER_RANGE.matcher(text);
        if (numbers.matches()) {
            int from = Integer.parseInt(numbers.group(1));
            int to = Integer.parseInt(numbers.group(2));
            require(from <= to, "row range " + text + " goes backwards");
            List<String> result = new ArrayList<>();
            for (int i = from; i <= to; i++) {
                result.add(String.valueOf(i));
            }
            return result;
        }
        Matcher letters = LETTER_RANGE.matcher(text);
        if (letters.matches()) {
            char from = letters.group(1).charAt(0);
            char to = letters.group(2).charAt(0);
            require(from <= to, "row range " + text + " goes backwards");
            List<String> result = new ArrayList<>();
            for (char c = from; c <= to; c++) {
                result.add(String.valueOf(c));
            }
            return result;
        }
        require(text.length() <= MAX_LABEL_LENGTH, "row label " + text + " is longer than " + MAX_LABEL_LENGTH);
        return List.of(text);
    }

    private static void requireName(String name, String what) {
        require(name != null && !name.isBlank() && name.length() <= MAX_NAME_LENGTH,
                what + " must be 1-" + MAX_NAME_LENGTH + " characters");
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalArgumentException(problem);
        }
    }
}
