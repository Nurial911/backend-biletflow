package kz.edu.biletflow.backend.configs;

import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fills an EMPTY database with demo data so the whole team sees the same thing.
 * Runs on startup only when app.seed.enabled=true (env var SEED_DATA=true).
 * Safe to restart: if the demo admin already exists, it does nothing.
 * NEVER enable on a real production system: all demo accounts share one known password.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail("admin@biletflow.kz")) {
            log.info("Seed data already present, skipping.");
            return;
        }
        log.info("Seeding demo data...");

        // ---------- users ----------
        createUser("admin@biletflow.kz", User.Role.PLATFORM_ADMIN);
        createUser("attendee@biletflow.kz", User.Role.ATTENDEE);
        createUser("scanner@biletflow.kz", User.Role.EVENT_ADMIN);
        User org1 = createUser("organizer1@biletflow.kz", User.Role.ORGANIZER);
        User org2 = createUser("organizer2@biletflow.kz", User.Role.ORGANIZER);
        createProfile(org1, "Astana Student Events Club, +7 700 000 00 01");
        createProfile(org2, "Nur-Sultan Music Productions, +7 700 000 00 02");

        // ---------- venues ----------
        Venue hall = venue("Demo Concert Hall", "Demo street 1, Astana");
        Venue arena = venue("Demo Arena", "Demo avenue 25, Astana");
        Venue auditorium = venue("Demo University Auditorium", "Demo campus, Astana");

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        // ---------- events (organizer 1) ----------
        Event workshop = event(org1, auditorium, "Free Java Workshop",
                "Hands-on intro to Spring Boot. Bring a laptop.", "Education",
                now.plusDays(10).withHour(15).withMinute(0), 3,
                150, VisibilityStatus.PUBLIC, EventStatus.PUBLISHED);
        ticket(workshop, "Free entry", "Open to everyone", "0", 150,
                now.minusDays(1), workshop.getStartTime().minusHours(1), 4);

        Event concert = event(org1, hall, "Spring Jazz Night",
                "An evening of live jazz with local artists.", "Music",
                now.plusDays(21).withHour(19).withMinute(0), 3,
                300, VisibilityStatus.PUBLIC, EventStatus.PUBLISHED);
        ticket(concert, "Standard", "General admission", "5000", 200,
                now.minusDays(1), concert.getStartTime().minusHours(2), 6);
        ticket(concert, "VIP", "Front rows + welcome drink", "15000", 50,
                now.minusDays(1), concert.getStartTime().minusHours(2), 4);
        TicketType earlyBird = ticket(concert, "Early bird", "Limited discounted tickets", "3500", 50,
                now.minusDays(10), now.minusDays(2), 4);
        earlyBird.setHidden(true);      // hidden ticket type, to test the "hidden" feature
        ticketTypeRepository.save(earlyBird);

        Event draft = event(org1, hall, "Draft: Autumn Hackathon",
                "Still being planned, not public yet.", "Technology",
                now.plusDays(45).withHour(10).withMinute(0), 8,
                100, VisibilityStatus.PUBLIC, EventStatus.DRAFT);
        ticket(draft, "Team pass", "Up to 4 people", "2000", 100,
                null, null, 4);

        // ---------- events (organizer 2) ----------
        Event festival = event(org2, arena, "Kazakh Folk Festival",
                "A full day of music, food and crafts.", "Festival",
                now.plusDays(30).withHour(12).withMinute(0), 8,
                1000, VisibilityStatus.PUBLIC, EventStatus.PUBLISHED);
        ticket(festival, "Day pass", "Entry for the whole day", "2500", 800,
                now.minusDays(1), festival.getStartTime().minusHours(1), 10);
        ticket(festival, "Family pass", "2 adults + 2 children", "7000", 200,
                now.minusDays(1), festival.getStartTime().minusHours(1), 3);

        Event privateParty = event(org2, hall, "Private Alumni Meetup",
                "Invite-only. Open by link, not shown in the public list.", "Social",
                now.plusDays(14).withHour(18).withMinute(30), 3,
                60, VisibilityStatus.UNLISTED, EventStatus.PUBLISHED);
        ticket(privateParty, "Guest", "By invitation link", "0", 60,
                now.minusDays(1), privateParty.getStartTime().minusHours(1), 2);

        Event cancelled = event(org2, arena, "Cancelled: Rock Night",
                "Cancelled because of artist illness.", "Music",
                now.plusDays(7).withHour(20).withMinute(0), 3,
                500, VisibilityStatus.PUBLIC, EventStatus.CANCELLED);
        ticket(cancelled, "Standard", "General admission", "6000", 500,
                now.minusDays(3), cancelled.getStartTime().minusHours(2), 6);

        log.info("Seeding finished. All demo accounts use password: {}", DEMO_PASSWORD);
    }

    private User createUser(String email, User.Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        u.setRole(role);
        return userRepository.save(u);
    }

    private void createProfile(User user, String contact) {
        OrganizerProfile p = new OrganizerProfile();
        p.setUser(user);
        p.setContactInformation(contact);
        organizerProfileRepository.save(p);
    }

    private Venue venue(String name, String address) {
        Venue v = new Venue();
        v.setName(name);
        v.setAddress(address);
        return venueRepository.save(v);
    }

    private Event event(User organizer, Venue venue, String title, String description, String category,
                        LocalDateTime start, int durationHours, int capacity,
                        VisibilityStatus visibility, EventStatus status) {
        Event e = new Event();
        e.setOrganizer(organizer);
        e.setVenue(venue);
        e.setTitle(title);
        e.setDescription(description);
        e.setCategory(category);
        e.setStartTime(start);
        e.setEndTime(start.plusHours(durationHours));
        e.setRegistrationOpeningTime(LocalDateTime.now().minusDays(1));
        e.setRegistrationClosingTime(start.minusHours(1));
        e.setCapacity(capacity);
        e.setVisibilityStatus(visibility);
        e.setStatus(status);
        return eventRepository.save(e);
    }

    private TicketType ticket(Event event, String name, String description, String price, int quantity,
                              LocalDateTime salesStart, LocalDateTime salesEnd, int maxPerOrder) {
        TicketType t = new TicketType();
        t.setEvent(event);
        t.setName(name);
        t.setDescription(description);
        t.setPrice(new BigDecimal(price));
        t.setQuantity(quantity);
        t.setSalesStart(salesStart);
        t.setSalesEnd(salesEnd);
        t.setMaxPerOrder(maxPerOrder);
        return ticketTypeRepository.save(t);
    }
}
