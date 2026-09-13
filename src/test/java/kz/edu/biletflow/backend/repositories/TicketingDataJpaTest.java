package kz.edu.biletflow.backend.repositories;

import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.TicketType;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.entities.Venue;
import kz.edu.biletflow.backend.entities.VisibilityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TicketingDataJpaTest {
    @Autowired UserRepository userRepository;
    @Autowired VenueRepository venueRepository;
    @Autowired EventRepository eventRepository;
    @Autowired TicketTypeRepository ticketTypeRepository;

    @Test
    void persistsTicketTypeWithEnumBackedEvent() {
        User organizer = new User();
        organizer.setEmail("organizer@example.com");
        organizer.setPasswordHash("hash");
        organizer.setRole(User.Role.ORGANIZER);
        organizer.setCreatedAt(LocalDateTime.now());
        organizer = userRepository.save(organizer);

        Venue venue = new Venue();
        venue.setName("Main Hall");
        venue = venueRepository.save(venue);

        Event event = new Event();
        event.setOrganizer(organizer);
        event.setVenue(venue);
        event.setTitle("Concert");
        event.setStartTime(LocalDateTime.now().plusDays(2));
        event.setVisibilityStatus(VisibilityStatus.PUBLIC);
        event.setStatus(EventStatus.PUBLISHED);
        event = eventRepository.save(event);

        TicketType ticketType = new TicketType();
        ticketType.setEvent(event);
        ticketType.setName("Free entry");
        ticketType.setPrice(BigDecimal.ZERO);
        ticketType.setQuantity(20);
        ticketType.setLimitPerOrder(2);

        TicketType saved = ticketTypeRepository.save(ticketType);

        assertThat(ticketTypeRepository.findById(saved.getId())).isPresent()
                .get().extracting(TicketType::getPrice).isEqualTo(BigDecimal.ZERO);
    }
}
