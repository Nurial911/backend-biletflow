package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.EventPhase;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Attendance of one event (SRS 4.15), from tickets and check-in records.
 * Ticket holders = tickets that grant entry (VALID + USED); refunded and cancelled tickets don't count.
 * absent = holders not checked in: "not arrived yet" while the event runs, absent once it is over.
 * checkInRate = checkedIn / holders in percent, one decimal, 0 when there are no holders.
 * With ?ticketTypeId= every figure, ticketTypes and arrivals cover that ticket type only.
 */
@Data
public class EventAttendanceResponse {
    private Long eventId;
    private String eventTitle;
    private EventPhase phase;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long ticketTypeId;          // the filter applied, null = all ticket types

    private long ticketHolders;
    private long checkedIn;
    private long absent;
    private BigDecimal checkInRate;
    private long undoneCheckIns;        // check-ins reversed by staff (check_in_records)
    private LocalDateTime firstArrival; // null until someone is checked in
    private LocalDateTime lastArrival;

    private int intervalMinutes;
    private List<TicketTypeAttendance> ticketTypes;
    // Only intervals with arrivals, oldest first; from is inclusive, to exclusive.
    private List<ArrivalInterval> arrivals;

    public record TicketTypeAttendance(Long ticketTypeId, String name, boolean hidden,
                                       long ticketHolders, long checkedIn, long absent, BigDecimal checkInRate) {
    }

    public record ArrivalInterval(LocalDateTime from, LocalDateTime to, long checkedIn, long cumulative) {
    }
}
