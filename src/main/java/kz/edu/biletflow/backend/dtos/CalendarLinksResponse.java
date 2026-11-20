package kz.edu.biletflow.backend.dtos;

import lombok.Data;

// One-click "add to calendar" options for the event page and order confirmation (SRS 4.11).
// For a cancelled event only icsUrl is set: its file marks the entry added earlier as cancelled.
@Data
public class CalendarLinksResponse {
    private Long eventId;
    private boolean cancelled;
    private int sequence;
    private String icsUrl;
    private String googleCalendarUrl;
    private String outlookUrl;
    private String office365Url;
}
