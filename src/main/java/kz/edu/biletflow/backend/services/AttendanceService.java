package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.EventAttendanceResponse;

// Attendance analytics for the organizer dashboard (SRS 4.15).
public interface AttendanceService {
    // intervalMinutes: size of the arrival intervals, one of AttendanceServiceImpl.INTERVALS (null = 15).
    EventAttendanceResponse getEventAttendance(Long eventId, Long requesterId, boolean isPlatformAdmin,
                                               @Nullable Long ticketTypeId, @Nullable Integer intervalMinutes);
}
