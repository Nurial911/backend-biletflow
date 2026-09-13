ALTER TABLE users ADD COLUMN is_suspended BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(80) NOT NULL,
    message TEXT,
    timestamp TIMESTAMP NOT NULL
);

CREATE TABLE support_cases (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    assigned_staff_id BIGINT REFERENCES users(id),
    case_type VARCHAR(80) NOT NULL,
    event_id BIGINT REFERENCES events(id),
    order_id BIGINT REFERENCES orders(id),
    ticket_id BIGINT REFERENCES tickets(id),
    issue_category VARCHAR(255),
    status VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE support_messages (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES support_cases(id),
    sender_id BIGINT NOT NULL REFERENCES users(id),
    message_content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL
);

CREATE TABLE promotional_campaigns (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    discount_type VARCHAR(50) NOT NULL,
    discount_value NUMERIC(12, 2) NOT NULL,
    validity_start TIMESTAMP,
    validity_end TIMESTAMP,
    max_redemptions INTEGER,
    applicable_ticket_types VARCHAR(255)
);

CREATE TABLE promo_codes (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES promotional_campaigns(id),
    code_string VARCHAR(255) NOT NULL UNIQUE,
    campaign_qr_code_token VARCHAR(255)
);

CREATE TABLE promo_redemptions (
    id BIGSERIAL PRIMARY KEY,
    promo_code_id BIGINT NOT NULL REFERENCES promo_codes(id),
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id)
);
