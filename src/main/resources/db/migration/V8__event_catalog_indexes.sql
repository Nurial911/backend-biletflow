-- The catalog filters events by start time, and its price filter and EventResponse.minPrice
-- look up each event's ticket types. Indexes only: nothing for old app versions to notice.
CREATE INDEX IF NOT EXISTS idx_ticket_types_event_id ON ticket_types (event_id);
CREATE INDEX IF NOT EXISTS idx_events_start_time ON events (start_time);
