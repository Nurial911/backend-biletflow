package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventPhase;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.Venue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Calendar entries for events (SRS 4.11): an iCalendar (RFC 5545) file and "add to calendar" links for
 * Google Calendar and Outlook. Nothing here needs access to the attendee's calendar account.
 * The UID is stable per event and SEQUENCE is {@link Event#getCalendarSequence()}, so a newer file
 * (changed details, cancellation) replaces the entry added earlier. Touches the lazy venue: call it
 * inside a transaction.
 */
@Component
public class EventCalendar {
    // Event times are stored as Almaty wall-clock time without a zone (see CLAUDE.md).
    public static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Almaty");
    public static final String CONTENT_TYPE = "text/calendar; charset=utf-8";

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter WITH_OFFSET = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final int MAX_LINE_OCTETS = 75;
    // Links carry the description in the query string; long ones would make URLs that browsers refuse.
    private static final int MAX_LINK_DESCRIPTION = 1000;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public String fileName(Event event) {
        return "biletflow-event-" + event.getId() + ".ics";
    }

    public String eventUrl(Event event) {
        return frontendUrl.replaceAll("/+$", "") + "/events/" + event.getId();
    }

    public byte[] ics(Event event) {
        boolean cancelled = event.getStatus() == EventStatus.CANCELLED;
        LocalDateTime start = event.getStartTime();
        LocalDateTime end = EventPhase.effectiveEnd(event);
        String zone = EVENT_ZONE.getId();

        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//BiletFlow//Event calendar//EN");
        line(out, "CALSCALE:GREGORIAN");
        // PUBLISH also for a cancellation: a downloaded file is not a meeting invitation, and clients
        // replace the earlier entry by UID and SEQUENCE and show it as cancelled.
        line(out, "METHOD:PUBLISH");
        timeZone(out, start);
        line(out, "BEGIN:VEVENT");
        line(out, "UID:" + uid(event));
        line(out, "DTSTAMP:" + UTC.format(Instant.now()));
        line(out, "SEQUENCE:" + event.getCalendarSequence());
        line(out, "DTSTART;TZID=" + zone + ":" + LOCAL.format(start));
        line(out, "DTEND;TZID=" + zone + ":" + LOCAL.format(end));
        line(out, "SUMMARY:" + text((cancelled ? "Cancelled: " : "") + event.getTitle()));
        line(out, "DESCRIPTION:" + text(description(event, Integer.MAX_VALUE)));
        line(out, "LOCATION:" + text(location(event.getVenue())));
        line(out, "URL:" + eventUrl(event));
        line(out, "STATUS:" + (cancelled ? "CANCELLED" : "CONFIRMED"));
        line(out, "TRANSP:OPAQUE");
        line(out, "END:VEVENT");
        line(out, "END:VCALENDAR");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String googleCalendarLink(Event event) {
        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + encode(event.getTitle())
                + "&dates=" + LOCAL.format(event.getStartTime()) + "/" + LOCAL.format(EventPhase.effectiveEnd(event))
                + "&ctz=" + encode(EVENT_ZONE.getId())
                + "&details=" + encode(description(event, MAX_LINK_DESCRIPTION))
                + "&location=" + encode(location(event.getVenue()));
    }

    // outlook.live.com for personal accounts, outlook.office.com for work and school accounts.
    public String outlookLink(Event event, String host) {
        return "https://" + host + "/calendar/0/action/compose?path=%2Fcalendar%2Faction%2Fcompose&rru=addevent"
                + "&subject=" + encode(event.getTitle())
                + "&startdt=" + encode(withOffset(event.getStartTime()))
                + "&enddt=" + encode(withOffset(EventPhase.effectiveEnd(event)))
                + "&body=" + encode(description(event, MAX_LINK_DESCRIPTION))
                + "&location=" + encode(location(event.getVenue()));
    }

    // Stable for the event's lifetime, so every later file updates the same calendar entry.
    String uid(Event event) {
        String host = null;
        try {
            host = URI.create(frontendUrl).getHost();
        } catch (IllegalArgumentException ignored) {
            // fall back below
        }
        return "event-" + event.getId() + "@" + (host != null ? host : "biletflow");
    }

    private String description(Event event, int maxDescription) {
        StringBuilder text = new StringBuilder();
        if (event.getStatus() == EventStatus.CANCELLED) {
            text.append("This event has been cancelled by the organizer.\n\n");
        }
        String own = event.getDescription();
        if (own != null && !own.isBlank()) {
            own = own.strip();
            text.append(own.length() > maxDescription ? own.substring(0, maxDescription) + "..." : own).append("\n\n");
        }
        return text.append("Event page: ").append(eventUrl(event)).toString();
    }

    private static String location(Venue venue) {
        return Stream.of(venue.getName(), venue.getAddress())
                .filter(part -> part != null && !part.isBlank())
                .map(String::strip)
                .collect(Collectors.joining(", "));
    }

    // Almaty has had a single fixed offset since 2024 (no daylight saving), so one observance covers the event.
    private static void timeZone(StringBuilder out, LocalDateTime start) {
        Instant at = start.atZone(EVENT_ZONE).toInstant();
        ZoneOffset offset = EVENT_ZONE.getRules().getOffset(at);
        ZoneOffsetTransition previous = EVENT_ZONE.getRules().previousTransition(at);
        line(out, "BEGIN:VTIMEZONE");
        line(out, "TZID:" + EVENT_ZONE.getId());
        line(out, "BEGIN:STANDARD");
        line(out, "DTSTART:" + LOCAL.format(previous != null ? previous.getDateTimeBefore() : LocalDateTime.of(1970, 1, 1, 0, 0)));
        line(out, "TZOFFSETFROM:" + offset(previous != null ? previous.getOffsetBefore() : offset));
        line(out, "TZOFFSETTO:" + offset(offset));
        line(out, "TZNAME:" + offset(offset).substring(0, 3));
        line(out, "END:STANDARD");
        line(out, "END:VTIMEZONE");
    }

    private static String offset(ZoneOffset offset) {
        int seconds = offset.getTotalSeconds();
        int minutes = Math.abs(seconds) / 60;
        return String.format("%s%02d%02d", seconds < 0 ? "-" : "+", minutes / 60, minutes % 60);
    }

    private static String withOffset(LocalDateTime time) {
        return WITH_OFFSET.format(time.atZone(EVENT_ZONE));
    }

    // RFC 5545 TEXT: backslash, semicolon, comma and line breaks are escaped; other control characters dropped.
    static String text(String value) {
        StringBuilder out = new StringBuilder(value.length());
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case ';' -> out.append("\\;");
                case ',' -> out.append("\\,");
                case '\n' -> out.append("\\n");
                default -> {
                    if (c >= 0x20 && c != 0x7F || c == '\t') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    // Content lines are folded at 75 octets (UTF-8), never inside a character, and end with CRLF.
    static void line(StringBuilder out, String content) {
        int octets = 0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            int length = codePoint < 0x80 ? 1 : codePoint < 0x800 ? 2 : codePoint < 0x10000 ? 3 : 4;
            if (octets + length > MAX_LINE_OCTETS) {
                out.append("\r\n ");
                octets = 1;
            }
            out.appendCodePoint(codePoint);
            octets += length;
            i += Character.charCount(codePoint);
        }
        out.append("\r\n");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
