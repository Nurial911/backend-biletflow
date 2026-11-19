package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.EventAttendanceResponse;
import kz.edu.biletflow.backend.dtos.EventAttendanceResponse.ArrivalInterval;
import kz.edu.biletflow.backend.dtos.EventAttendanceResponse.TicketTypeAttendance;
import kz.edu.biletflow.backend.entities.CheckInAction;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventPhase;
import kz.edu.biletflow.backend.entities.TicketType;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.CheckInRecordRepository;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.repositories.TicketTypeRepository;
import kz.edu.biletflow.backend.services.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Attendance figures come from grouped queries over tickets and check-in records, so the cost doesn't grow
 * with the number of attendees. A ticket's usedAt is its current check-in (an undo clears it), so arrivals
 * and totals always agree.
 */
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {
    static final Set<Integer> INTERVALS = Set.of(5, 10, 15, 30, 60, 120, 1440);
    private static final int DEFAULT_INTERVAL = 15;

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;
    private final CheckInRecordRepository checkInRecordRepository;

    @Override
    @Transactional(readOnly = true)
    public EventAttendanceResponse getEventAttendance(Long eventId, Long requesterId, boolean isPlatformAdmin,
                                                      @Nullable Long ticketTypeId, @Nullable Integer intervalMinutes) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        // Organizer of the event or a platform admin; anyone else gets 404 (don't reveal the event exists).
        if (!isPlatformAdmin && !event.getOrganizer().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        int interval = intervalMinutes == null ? DEFAULT_INTERVAL : intervalMinutes;
        if (!INTERVALS.contains(interval)) {
            throw new BusinessRuleViolationException("interval must be one of " + INTERVALS.stream().sorted().toList() + " minutes.");
        }

        List<TicketType> types = ticketTypeRepository.findAllByEventIdOrderByIdAsc(eventId);
        if (ticketTypeId != null) {
            types = types.stream().filter(t -> t.getId().equals(ticketTypeId)).toList();
            if (types.isEmpty()) {
                throw new ResourceNotFoundException("Ticket type " + ticketTypeId + " not found in event " + eventId);
            }
        }

        Map<Long, Object[]> byType = new HashMap<>();
        for (Object[] row : ticketRepository.aggregateAttendanceByTicketType(eventId)) {
            byType.put((Long) row[0], row);
        }

        EventAttendanceResponse response = new EventAttendanceResponse();
        response.setEventId(event.getId());
        response.setEventTitle(event.getTitle());
        response.setPhase(EventPhase.of(event, LocalDateTime.now()));
        response.setStartTime(event.getStartTime());
        response.setEndTime(event.getEndTime());
        response.setTicketTypeId(ticketTypeId);
        response.setIntervalMinutes(interval);

        long holders = 0;
        long checkedIn = 0;
        LocalDateTime first = null;
        LocalDateTime last = null;
        List<TicketTypeAttendance> typeRows = new ArrayList<>();
        for (TicketType type : types) {
            Object[] row = byType.get(type.getId());
            long typeHolders = row == null ? 0 : toLong(row[1]);
            long typeCheckedIn = row == null ? 0 : toLong(row[2]);
            typeRows.add(new TicketTypeAttendance(type.getId(), type.getName(), type.isHidden(),
                    typeHolders, typeCheckedIn, typeHolders - typeCheckedIn, rate(typeCheckedIn, typeHolders)));
            holders += typeHolders;
            checkedIn += typeCheckedIn;
            if (row != null) {
                first = earliest(first, toDateTime(row[3]));
                last = latest(last, toDateTime(row[4]));
            }
        }
        response.setTicketTypes(typeRows);
        response.setTicketHolders(holders);
        response.setCheckedIn(checkedIn);
        response.setAbsent(holders - checkedIn);
        response.setCheckInRate(rate(checkedIn, holders));
        response.setFirstArrival(first);
        response.setLastArrival(last);

        long typeFilter = ticketTypeId == null ? 0 : ticketTypeId;
        response.setUndoneCheckIns(checkInRecordRepository.countByEventAndAction(eventId, CheckInAction.UNDO, typeFilter));

        List<ArrivalInterval> arrivals = new ArrayList<>();
        long cumulative = 0;
        for (Object[] row : ticketRepository.countArrivalsByInterval(eventId, typeFilter, interval)) {
            LocalDateTime from = toDateTime(row[0]);
            long count = toLong(row[1]);
            cumulative += count;
            arrivals.add(new ArrivalInterval(from, from.plusMinutes(interval), count, cumulative));
        }
        response.setArrivals(arrivals);
        return response;
    }

    private static BigDecimal rate(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static LocalDateTime toDateTime(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Timestamp ts ? ts.toLocalDateTime() : (LocalDateTime) value;
    }

    private static LocalDateTime earliest(LocalDateTime a, LocalDateTime b) {
        return a == null ? b : (b == null || a.isBefore(b) ? a : b);
    }

    private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
        return a == null ? b : (b == null || a.isAfter(b) ? a : b);
    }
}
