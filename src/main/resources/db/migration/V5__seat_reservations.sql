CREATE TABLE venue_sections (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    name VARCHAR(255) NOT NULL
);

CREATE TABLE venue_rows (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL REFERENCES venue_sections(id),
    name_or_number VARCHAR(255) NOT NULL
);

CREATE TABLE seats (
    id BIGSERIAL PRIMARY KEY,
    row_id BIGINT NOT NULL REFERENCES venue_rows(id),
    seat_number VARCHAR(255) NOT NULL,
    is_accessible BOOLEAN NOT NULL DEFAULT FALSE,
    price_category VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE seat_holds (
    id BIGSERIAL PRIMARY KEY,
    seat_id BIGINT NOT NULL REFERENCES seats(id),
    attendee_id BIGINT NOT NULL REFERENCES attendees(id),
    expiration_time TIMESTAMP NOT NULL
);

ALTER TABLE order_items ADD COLUMN assigned_seat_id BIGINT REFERENCES seats(id);
