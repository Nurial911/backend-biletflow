CREATE TABLE attendees (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    specific_attendee_details TEXT
);

CREATE TABLE ticket_types (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    sales_start_time TIMESTAMP,
    sales_end_time TIMESTAMP,
    limit_per_order INTEGER NOT NULL,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    attendee_id BIGINT NOT NULL REFERENCES attendees(id),
    event_id BIGINT NOT NULL REFERENCES events(id),
    total_amount NUMERIC(12, 2) NOT NULL,
    processing_charges NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    ticket_type_id BIGINT NOT NULL REFERENCES ticket_types(id),
    price NUMERIC(12, 2) NOT NULL,
    quantity INTEGER NOT NULL
);

CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_identifier VARCHAR(255) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    ticket_type_id BIGINT NOT NULL REFERENCES ticket_types(id),
    event_id BIGINT NOT NULL REFERENCES events(id),
    attendee_id BIGINT NOT NULL REFERENCES attendees(id),
    status VARCHAR(50) NOT NULL
);
