-- Calendar export (SRS 4.11).

-- The iCalendar SEQUENCE of the event: raised on every change of its details and on cancellation, so a newer
-- .ics file replaces the entry an attendee added earlier (same UID, higher SEQUENCE).
ALTER TABLE events ADD COLUMN calendar_sequence integer NOT NULL DEFAULT 0;

-- The event a notification is about: its email then carries the event's current .ics file
-- (event changed, event cancelled). Plain value like user_id, so the entry stays readable whatever happens.
ALTER TABLE notifications ADD COLUMN event_id bigint;
