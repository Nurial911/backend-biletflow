CREATE TABLE payout_accounts (
    id BIGSERIAL PRIMARY KEY,
    organizer_id BIGINT NOT NULL REFERENCES users(id),
    account_details TEXT NOT NULL,
    verification_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE paid_sales_activations (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL UNIQUE REFERENCES events(id),
    payout_account_id BIGINT REFERENCES payout_accounts(id),
    status VARCHAR(50) NOT NULL,
    activation_fee NUMERIC(12, 2) NOT NULL,
    activated_at TIMESTAMP
);

CREATE TABLE inventory_reservations (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    ticket_type_id BIGINT NOT NULL REFERENCES ticket_types(id),
    quantity INTEGER NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    acting_user_id BIGINT REFERENCES users(id),
    action_type VARCHAR(255) NOT NULL,
    affected_entity VARCHAR(255) NOT NULL,
    short_description TEXT NOT NULL
);
