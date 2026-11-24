package kz.edu.biletflow.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Assigned seating (SRS 4.3.1): seat maps, seat holds through unpaid orders, standing sections, and the rules around them.
class SeatingIntegrationTest extends ApiIntegrationTest {

    @Test
    void layoutsAreListedAndAVenueHoldsWhatItsMapHolds() {
        List<JsonNode> layouts = list(call(HttpMethod.GET, "/api/venues/seating-layouts", null, null).assertStatus(200).json());
        Map<String, JsonNode> byKey = layouts.stream().collect(Collectors.toMap(l -> l.get("key").asString(), l -> l));
        assertThat(byKey).containsKeys("arena", "concert-hall");
        assertThat(byKey.get("arena").get("capacity").asInt()).isEqualTo(16000);
        assertThat(byKey.get("arena").get("seatCount").asInt()).isEqualTo(10000);
        assertThat(byKey.get("arena").get("standingCapacity").asInt()).isEqualTo(6000);
        assertThat(byKey.get("concert-hall").get("capacity").asInt()).isEqualTo(488);

        String organizer = organizer();
        JsonNode venue = call(HttpMethod.POST, "/api/venues", organizer,
                Map.of("name", "Hall", "address", "Abay 1, Almaty", "seatingLayout", "concert-hall"))
                .assertStatus(201).json();
        assertThat(venue.get("capacity").asInt()).isEqualTo(488);
        assertThat(venue.get("seatingLayout").asString()).isEqualTo("concert-hall");

        assertThat(call(HttpMethod.POST, "/api/venues", organizer,
                Map.of("name", "Hall", "address", "Abay 1", "seatingLayout", "concert-hall", "capacity", 100))
                .assertStatus(400).text()).contains("holds 488 people");
        call(HttpMethod.POST, "/api/venues", organizer, Map.of("name", "Hall", "address", "Abay 1", "seatingLayout", "opera"))
                .assertStatus(400);
        assertThat(call(HttpMethod.POST, "/api/venues", organizer, Map.of("name", "Field", "address", "Steppe"))
                .assertStatus(400).text()).contains("capacity is required");

        // Only a platform admin changes a venue's seat map afterwards.
        long venueId = venue.get("id").asLong();
        call(HttpMethod.PUT, "/api/venues/" + venueId + "/seating-layout", organizer, Map.of("seatingLayout", "arena"))
                .assertStatus(403);
        JsonNode changed = call(HttpMethod.PUT, "/api/venues/" + venueId + "/seating-layout", admin(),
                Map.of("seatingLayout", "arena")).assertStatus(200).json();
        assertThat(changed.get("capacity").asInt()).isEqualTo(16000);
    }

    @Test
    void buyersPickSeatsThatAreHeldThenSoldAndShownOnTheirTickets() {
        String organizer = organizer();
        long event = seatedEvent(organizer, "concert-hall");
        JsonNode setup = call(HttpMethod.GET, "/api/events/" + event + "/seating", organizer, null).assertStatus(200).json();
        assertThat(setup.get("seatingMode").asString()).isEqualTo("ASSIGNED");
        assertThat(setup.get("capacity").asInt()).isEqualTo(488);
        assertThat(setup.get("stage").get("label").asString()).isEqualTo("Stage");
        assertThat(list(setup.get("sections"))).extracting(s -> s.get("key").asString()).containsExactly("STALLS", "BALCONY");

        long stalls = paidType(organizer, event, "Stalls", 10000);
        long balcony = paidType(organizer, event, "Balcony", 5000);
        assertThat(ticketTypeQuantity(event, stalls)).isZero(); // nothing is linked to it yet
        linkSection(organizer, event, "STALLS", stalls);
        assertThat(ticketTypeQuantity(event, stalls)).isEqualTo(308);

        // The public map: the balcony isn't on sale, so its seats can't be bought.
        JsonNode map = seatMap(event);
        JsonNode stallsSection = section(map, "STALLS");
        long stallsId = stallsSection.get("id").asLong();
        assertThat(stallsSection.get("onSale").asBoolean()).isTrue();
        assertThat(stallsSection.get("price").decimalValue()).isEqualByComparingTo("10000");
        assertThat(stallsSection.get("available").asLong()).isEqualTo(308);
        assertThat(section(map, "BALCONY").get("onSale").asBoolean()).isFalse();

        Map<String, JsonNode> seats = seats(null, event, stallsId);
        assertThat(seats).hasSize(308);
        assertThat(seats.get("M-1").get("accessible").asBoolean()).isTrue();
        assertThat(seats.get("A-1").get("accessible").asBoolean()).isFalse();
        assertThat(seats.get("A-1").get("status").asString()).isEqualTo("AVAILABLE");

        // Ordering holds the seats: the buyer sees them as theirs, everyone else as held.
        String buyer = register();
        JsonNode order = call(HttpMethod.POST, "/api/orders", buyer, seatOrder(event, seats, "A-1", "A-2"))
                .assertStatus(201).json();
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("20000");
        assertThat(list(order.get("items"))).extracting(i -> i.get("seatLabel").asString())
                .containsExactlyInAnyOrder("Stalls, row A, seat 1", "Stalls, row A, seat 2");
        assertThat(seats(buyer, event, stallsId).get("A-1").get("status").asString()).isEqualTo("YOURS");
        assertThat(seats(register(), event, stallsId).get("A-1").get("status").asString()).isEqualTo("HELD");
        JsonNode heldMap = seatMap(event);
        assertThat(section(heldMap, "STALLS").get("held").asLong()).isEqualTo(2);
        assertThat(section(heldMap, "STALLS").get("available").asLong()).isEqualTo(306);

        // Someone else trying the same seat gets 409 naming it.
        String other = register();
        assertThat(call(HttpMethod.POST, "/api/orders", other, seatOrder(event, seats, "A-2", "A-3"))
                .assertStatus(409).text()).contains("Stalls, row A, seat 2").doesNotContain("seat 3");
        // A seat of a section that isn't on sale, a seated ticket type ordered by count, and one not on the map.
        long balconySeat = seats(null, event, section(map, "BALCONY").get("id").asLong()).get("A-1").get("id").asLong();
        call(HttpMethod.POST, "/api/orders", other, Map.of("eventId", event, "seatIds", List.of(balconySeat))).assertStatus(409);
        assertThat(call(HttpMethod.POST, "/api/orders", other, orderBody(event, stalls, 1)).assertStatus(400).text())
                .contains("Pick seats");
        assertThat(call(HttpMethod.POST, "/api/orders", other, orderBody(event, balcony, 1)).assertStatus(400).text())
                .contains("not on the seat map");

        // The quote prices seats the same way, without holding them.
        JsonNode quote = call(HttpMethod.POST, "/api/orders/quote", other, seatOrder(event, seats, "B-5")).assertStatus(200).json();
        assertThat(quote.get("totalAmount").decimalValue()).isEqualByComparingTo("10000");
        assertThat(seats(null, event, stallsId).get("B-5").get("status").asString()).isEqualTo("AVAILABLE");

        // Paid: sold, and the seat is on the ticket, its PDF and the check-in result.
        pay(buyer, order.get("id").asLong()).assertStatus(200);
        assertThat(seats(null, event, stallsId).get("A-1").get("status").asString()).isEqualTo("SOLD");
        List<JsonNode> tickets = tickets(buyer);
        assertThat(tickets).extracting(t -> t.get("seatLabel").asString())
                .containsExactlyInAnyOrder("Stalls, row A, seat 1", "Stalls, row A, seat 2");
        JsonNode ticket = tickets.getFirst();
        assertThat(ticket.get("rowLabel").asString()).isEqualTo("A");
        call(HttpMethod.GET, "/api/tickets/" + ticket.get("id").asLong() + "/pdf", buyer, null).assertStatus(200);
        JsonNode scan = scan(organizer, event, ticket.get("code").asString());
        assertThat(scan.get("result").asString()).isEqualTo("CHECKED_IN");
        assertThat(scan.get("ticket").get("seatLabel").asString()).startsWith("Stalls, row A, seat ");

        assertThat(notifications(buyer).stream().filter(n -> n.get("type").asString().equals("ORDER_CONFIRMED")).findFirst()
                .orElseThrow().get("body").asString()).contains("Seats: Stalls, row A, seat 1; Stalls, row A, seat 2");
        assertThat(auditActions(event)).contains("SEATING_MODE_CHANGED", "SEATING_SECTION_ASSIGNED");
    }

    @Test
    void twoBuyersRacingForTheSameSeatGetItOnce() throws Exception {
        String organizer = organizer();
        long event = seatedEvent(organizer, "concert-hall");
        long stalls = paidType(organizer, event, "Stalls", 10000);
        linkSection(organizer, event, "STALLS", stalls);
        Map<String, JsonNode> seats = seats(null, event, sectionId(event, "STALLS"));

        List<String> buyers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            buyers.add(register());
        }
        AtomicInteger next = new AtomicInteger();
        List<Integer> statuses = concurrently(8, () -> call(HttpMethod.POST, "/api/orders",
                buyers.get(next.getAndIncrement()), seatOrder(event, seats, "C-7", "C-8")).status());
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(7);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items WHERE event_seat_id IN (?, ?)", Long.class,
                seats.get("C-7").get("id").asLong(), seats.get("C-8").get("id").asLong())).isEqualTo(2);
    }

    @Test
    void cancelledExpiredAndRefundedOrdersFreeTheirSeats() {
        String organizer = organizer();
        long event = seatedEvent(organizer, "concert-hall");
        long stalls = paidType(organizer, event, "Stalls", 10000);
        linkSection(organizer, event, "STALLS", stalls);
        long sectionId = sectionId(event, "STALLS");
        Map<String, JsonNode> seats = seats(null, event, sectionId);
        String buyer = register();

        long cancelled = call(HttpMethod.POST, "/api/orders", buyer, seatOrder(event, seats, "D-1")).assertStatus(201).json().get("id").asLong();
        call(HttpMethod.POST, "/api/orders/" + cancelled + "/cancel", buyer, null).assertStatus(200);
        assertThat(seatStatus(event, sectionId, "D-1")).isEqualTo("AVAILABLE");

        long expired = call(HttpMethod.POST, "/api/orders", buyer, seatOrder(event, seats, "D-1")).assertStatus(201).json().get("id").asLong();
        jdbcTemplate.update("UPDATE orders SET expires_at = ? WHERE id = ?", LocalDateTime.now().minusMinutes(1), expired);
        assertThat(seatStatus(event, sectionId, "D-1")).isEqualTo("AVAILABLE");

        long paid = call(HttpMethod.POST, "/api/orders", buyer, seatOrder(event, seats, "D-1")).assertStatus(201).json().get("id").asLong();
        pay(buyer, paid).assertStatus(200);
        assertThat(seatStatus(event, sectionId, "D-1")).isEqualTo("SOLD");
        call(HttpMethod.POST, "/api/orders/" + paid + "/refund", organizer, Map.of()).assertStatus(200);
        assertThat(seatStatus(event, sectionId, "D-1")).isEqualTo("AVAILABLE");

        // Sold again: the refunded ticket and the new one exist side by side, only one of them admits.
        String second = register();
        long resold = call(HttpMethod.POST, "/api/orders", second, seatOrder(event, seats, "D-1")).assertStatus(201).json().get("id").asLong();
        pay(second, resold).assertStatus(200);
        assertThat(jdbcTemplate.queryForList("SELECT status FROM tickets WHERE event_seat_id = ? ORDER BY id", String.class,
                seats.get("D-1").get("id").asLong())).containsExactly("REFUNDED", "VALID");
    }

    @Test
    void anArenaSellsStandsBySeatAndTheFieldByCount() {
        String organizer = organizer();
        long started = System.nanoTime();
        long event = seatedEvent(organizer, "arena");
        long setupMillis = (System.nanoTime() - started) / 1_000_000;
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_seats WHERE event_id = ?", Long.class, event))
                .isEqualTo(10000);
        assertThat(setupMillis).as("creating 10 000 seats").isLessThan(15_000);

        long field = paidType(organizer, event, "Field", 8000);
        long north = paidType(organizer, event, "North stand", 12000);
        linkSection(organizer, event, "FIELD", field);
        linkSection(organizer, event, "N1", north);
        linkSection(organizer, event, "N2", north);
        assertThat(ticketTypeQuantity(event, field)).isEqualTo(6000);
        assertThat(ticketTypeQuantity(event, north)).isEqualTo(2000);

        // A standing section needs its own ticket type, and a standing type can't sell seats.
        assertThat(call(HttpMethod.PUT, "/api/events/" + event + "/seating/sections/" + sectionId(event, "N3"), organizer,
                Map.of("ticketTypeId", field)).assertStatus(400).text()).contains("sells a standing section");
        assertThat(call(HttpMethod.PUT, "/api/events/" + event + "/seating/sections/" + sectionId(event, "FIELD"), organizer,
                Map.of("ticketTypeId", north)).assertStatus(400).text()).contains("ticket type of its own");

        // Blocked seats are off sale and lower the quantity.
        Map<String, JsonNode> n1 = seats(null, event, sectionId(event, "N1"));
        List<Long> blocked = List.of(n1.get("1-1").get("id").asLong(), n1.get("1-2").get("id").asLong());
        call(HttpMethod.PUT, "/api/events/" + event + "/seating/seats", organizer, Map.of("seatIds", blocked, "blocked", true))
                .assertStatus(200);
        assertThat(ticketTypeQuantity(event, north)).isEqualTo(1998);
        assertThat(seats(null, event, sectionId(event, "N1")).get("1-1").get("status").asString()).isEqualTo("UNAVAILABLE");
        String buyer = register();
        call(HttpMethod.POST, "/api/orders", buyer, seatOrder(event, n1, "1-1")).assertStatus(409);

        // One order: two seats in the stand and three places on the field.
        Map<String, Object> body = new HashMap<>(seatOrder(event, n1, "2-10", "2-11"));
        body.put("items", List.of(Map.of("ticketTypeId", field, "quantity", 3)));
        JsonNode order = call(HttpMethod.POST, "/api/orders", buyer, body).assertStatus(201).json();
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("48000");
        assertThat(list(order.get("items"))).extracting(i -> i.get("seatLabel").asString())
                .containsExactlyInAnyOrder("Field (standing)", "North stand, sector 1, row 2, seat 10",
                        "North stand, sector 1, row 2, seat 11");
        JsonNode map = seatMap(event);
        assertThat(section(map, "FIELD").get("held").asLong()).isEqualTo(3);
        assertThat(section(map, "FIELD").get("available").asLong()).isEqualTo(5997);
        assertThat(seats(null, event, sectionId(event, "FIELD"))).isEmpty();

        // Seats and sections with sales or holds can't be blocked or relinked.
        assertThat(call(HttpMethod.PUT, "/api/events/" + event + "/seating/seats", organizer,
                Map.of("seatIds", List.of(n1.get("2-10").get("id").asLong()), "blocked", true)).assertStatus(400).text())
                .contains("North stand, sector 1, row 2, seat 10");
        call(HttpMethod.PUT, "/api/events/" + event + "/seating/sections/" + sectionId(event, "FIELD"), organizer,
                Map.of()).assertStatus(400);

        pay(buyer, order.get("id").asLong()).assertStatus(200);
        assertThat(tickets(buyer)).extracting(t -> t.get("seatLabel").asString())
                .contains("Field (standing)", "North stand, sector 1, row 2, seat 10");

        call(HttpMethod.PUT, "/api/events/" + event + "/seating/seats", organizer, Map.of("seatIds", blocked, "blocked", false))
                .assertStatus(200);
        assertThat(ticketTypeQuantity(event, north)).isEqualTo(2000);
        assertThat(auditActions(event)).contains("SEATS_BLOCKED", "SEATS_UNBLOCKED");
    }

    @Test
    void theSeatMapFollowsTheEventsRules() {
        String organizer = organizer();
        // General admission stays as it was, and a venue without a seat map can't switch.
        long plain = publishedEvent(organizer);
        long plainType = ticketType(organizer, plain, 0, 50);
        assertThat(call(HttpMethod.PUT, "/api/events/" + plain + "/seating", organizer, Map.of("mode", "ASSIGNED"))
                .assertStatus(400).text()).contains("has no seat map");
        call(HttpMethod.GET, "/api/events/" + plain + "/seat-map", null, null).assertStatus(400);
        assertThat(call(HttpMethod.POST, "/api/orders", register(), Map.of("eventId", plain, "seatIds", List.of(1L)))
                .assertStatus(400).text()).contains("general admission");
        createOrder(register(), plain, plainType, 2);

        // Setup is the organizer's: others get 403 on changes, 404 on the setup view.
        long event = seatedEvent(organizer, "concert-hall");
        String stranger = organizer();
        call(HttpMethod.GET, "/api/events/" + event + "/seating", stranger, null).assertStatus(404);
        call(HttpMethod.GET, "/api/events/" + event + "/seating", null, null).assertStatus(404);
        call(HttpMethod.PUT, "/api/events/" + event + "/seating", stranger, Map.of("mode", "GENERAL_ADMISSION")).assertStatus(403);
        call(HttpMethod.GET, "/api/events/" + event + "/seating", admin(), null).assertStatus(200);

        // The seat map fixes the capacity and the ticket type quantities.
        long stalls = ticketType(organizer, event, 0, 5);
        assertThat(ticketTypeQuantity(event, stalls)).isZero();
        assertThat(call(HttpMethod.PUT, "/api/events/" + event, organizer, Map.of("capacity", 100))
                .assertStatus(400).text()).contains("capacity comes from the seat map");
        call(HttpMethod.PUT, "/api/ticket-types/" + stalls, organizer, Map.of("quantity", 10)).assertStatus(400);

        // A blocked seat survives duplication; the section prices don't, since ticket types aren't copied.
        linkSection(organizer, event, "STALLS", stalls);
        Map<String, JsonNode> seats = seats(null, event, sectionId(event, "STALLS"));
        call(HttpMethod.PUT, "/api/events/" + event + "/seating/seats", organizer,
                Map.of("seatIds", List.of(seats.get("A-1").get("id").asLong()), "blocked", true)).assertStatus(200);
        long copy = call(HttpMethod.POST, "/api/events/" + event + "/duplicate", organizer, null).assertStatus(201).json().get("id").asLong();
        JsonNode copied = call(HttpMethod.GET, "/api/events/" + copy + "/seating", organizer, null).assertStatus(200).json();
        assertThat(copied.get("seatingMode").asString()).isEqualTo("ASSIGNED");
        assertThat(section(copied, "STALLS").get("blocked").asLong()).isEqualTo(1);
        assertThat(section(copied, "STALLS").get("onSale").asBoolean()).isFalse();
        call(HttpMethod.GET, "/api/events/" + copy + "/seat-map", null, null).assertStatus(404); // a draft

        // Free seats are confirmed at once, and then the seating can't change any more.
        String buyer = register();
        call(HttpMethod.POST, "/api/orders", buyer, seatOrder(event, seats, "A-2")).assertStatus(201);
        assertThat(tickets(buyer)).extracting(t -> t.get("seatLabel").asString()).containsExactly("Stalls, row A, seat 2");
        assertThat(call(HttpMethod.PUT, "/api/events/" + event + "/seating", organizer, Map.of("mode", "GENERAL_ADMISSION"))
                .assertStatus(400).text()).contains("once the event has orders");

        // Without orders, switching back removes the seat map.
        JsonNode back = call(HttpMethod.PUT, "/api/events/" + copy + "/seating", organizer, Map.of("mode", "GENERAL_ADMISSION"))
                .assertStatus(200).json();
        assertThat(back.get("sections").isEmpty()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_seats WHERE event_id = ?", Long.class, copy)).isZero();
    }

    @Test
    void occupancySplitsTheHallIntoSoldHeldAvailableBlockedAndNotOnSale() {
        String organizer = organizer();
        long event = seatedEvent(organizer, "concert-hall"); // 308 stalls + 180 balcony seats
        long stalls = paidType(organizer, event, "Stalls", 10000);
        linkSection(organizer, event, "STALLS", stalls); // the balcony stays without a ticket type

        Map<String, JsonNode> seats = seats(null, event, sectionId(event, "STALLS"));
        List<Long> blocked = List.of(seats.get("B-1").get("id").asLong(), seats.get("B-2").get("id").asLong());
        call(HttpMethod.PUT, "/api/events/" + event + "/seating/seats", organizer, Map.of("seatIds", blocked, "blocked", true))
                .assertStatus(200);
        String buyer = register();
        long paid = call(HttpMethod.POST, "/api/orders", buyer, seatOrder(event, seats, "A-1", "A-2"))
                .assertStatus(201).json().get("id").asLong();
        pay(buyer, paid).assertStatus(200);
        call(HttpMethod.POST, "/api/orders", register(), seatOrder(event, seats, "A-3")).assertStatus(201);

        JsonNode sales = call(HttpMethod.GET, "/api/organizer/events/" + event + "/sales", organizer, null)
                .assertStatus(200).json();
        JsonNode occupancy = sales.get("occupancy");
        assertThat(occupancy.get("capacity").asInt()).isEqualTo(488);
        assertThat(occupancy.get("sold").asLong()).isEqualTo(2);
        assertThat(occupancy.get("reserved").asLong()).isEqualTo(1);
        assertThat(occupancy.get("blocked").asLong()).isEqualTo(2);
        assertThat(occupancy.get("available").asLong()).isEqualTo(303);      // 308 - 2 blocked - 2 sold - 1 held
        assertThat(occupancy.get("availableHidden").asLong()).isZero();
        assertThat(occupancy.get("notOnSale").asLong()).isEqualTo(180);      // the balcony
        // The capacity-based figure still counts the balcony and the blocked seats.
        assertThat(sales.get("ticketsRemaining").asLong()).isEqualTo(485);

        // Hiding the ticket type moves its stock out of what buyers can get.
        call(HttpMethod.POST, "/api/ticket-types/" + stalls + "/hide", organizer, null).assertStatus(200);
        JsonNode hidden = call(HttpMethod.GET, "/api/organizer/events/" + event + "/sales", organizer, null)
                .assertStatus(200).json().get("occupancy");
        assertThat(hidden.get("available").asLong()).isZero();
        assertThat(hidden.get("availableHidden").asLong()).isEqualTo(303);
        assertThat(hidden.get("notOnSale").asLong()).isEqualTo(180);
    }

    // helpers //

    private long seatedEvent(String organizer, String layout) {
        long venue = call(HttpMethod.POST, "/api/venues", organizer,
                Map.of("name", "Seated venue", "address", "Kabanbay batyr 1, Astana", "seatingLayout", layout))
                .assertStatus(201).json().get("id").asLong();
        long event = call(HttpMethod.POST, "/api/events", organizer, Map.of(
                "title", "Seated event",
                "category", "Test",
                "startTime", LocalDateTime.now().plusDays(30).toString(),
                "visibilityStatus", "PUBLIC",
                "venueId", venue))
                .assertStatus(201).json().get("id").asLong();
        call(HttpMethod.PUT, "/api/events/" + event + "/seating", organizer, Map.of("mode", "ASSIGNED")).assertStatus(200);
        call(HttpMethod.POST, "/api/events/" + event + "/publish", organizer, null).assertStatus(200);
        return event;
    }

    // Paid ticket types of an assigned-seating event take no quantity: the seat map sets it.
    private long paidType(String organizer, long event, String name, int price) {
        long type = call(HttpMethod.POST, "/api/events/" + event + "/ticket-types", organizer,
                Map.of("name", name, "price", price, "maxPerOrder", 10))
                .assertStatus(201).json().get("id").asLong();
        jdbcTemplate.update("UPDATE events SET paid_sales_active = true WHERE id = ?", event);
        return type;
    }

    private void linkSection(String organizer, long event, String key, long ticketType) {
        call(HttpMethod.PUT, "/api/events/" + event + "/seating/sections/" + sectionId(event, key), organizer,
                Map.of("ticketTypeId", ticketType)).assertStatus(200);
    }

    private long sectionId(long event, String key) {
        return jdbcTemplate.queryForObject("SELECT id FROM event_sections WHERE event_id = ? AND section_key = ?",
                Long.class, event, key);
    }

    private int ticketTypeQuantity(long event, long ticketType) {
        return jdbcTemplate.queryForObject("SELECT quantity FROM ticket_types WHERE id = ? AND event_id = ?",
                Integer.class, ticketType, event);
    }

    private JsonNode seatMap(long event) {
        return call(HttpMethod.GET, "/api/events/" + event + "/seat-map", null, null).assertStatus(200).json();
    }

    private static JsonNode section(JsonNode map, String key) {
        return list(map.get("sections")).stream().filter(s -> s.get("key").asString().equals(key)).findFirst().orElseThrow();
    }

    // "row-number" -> seat, as the given viewer sees it (null = anonymous).
    private Map<String, JsonNode> seats(String viewer, long event, long sectionId) {
        JsonNode response = call(HttpMethod.GET, "/api/events/" + event + "/seat-map/sections/" + sectionId, viewer, null)
                .assertStatus(200).json();
        return list(response.get("seats")).stream()
                .collect(Collectors.toMap(s -> s.get("row").asString() + "-" + s.get("number").asInt(), s -> s));
    }

    private String seatStatus(long event, long sectionId, String seat) {
        return seats(null, event, sectionId).get(seat).get("status").asString();
    }

    private static Map<String, Object> seatOrder(long event, Map<String, JsonNode> seats, String... names) {
        List<Long> ids = Arrays.stream(names).map(n -> seats.get(n).get("id").asLong()).toList();
        return Map.of("eventId", event, "seatIds", ids);
    }
}
