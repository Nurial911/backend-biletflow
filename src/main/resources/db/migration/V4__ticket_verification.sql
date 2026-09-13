ALTER TABLE tickets ADD COLUMN qr_code TEXT NOT NULL DEFAULT '';

CREATE TABLE staff_assignments (
    id BIGSERIAL PRIMARY KEY,
    event_admin_id BIGINT NOT NULL REFERENCES users(id),
    event_id BIGINT NOT NULL REFERENCES events(id),
    UNIQUE (event_admin_id, event_id)
);

CREATE TABLE check_in_records (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id),
    event_admin_id BIGINT NOT NULL REFERENCES users(id),
    timestamp TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL
);
