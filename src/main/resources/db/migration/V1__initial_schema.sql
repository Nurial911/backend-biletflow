CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    verification_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE venues (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    address VARCHAR(255)
);

CREATE TABLE organizer_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    contact_information TEXT,
    verification_status VARCHAR(50) NOT NULL,
    payout_account_id BIGINT
);

CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    description VARCHAR(255),
    category VARCHAR(255),
    start_time TIMESTAMP,
    registration_opening_time TIMESTAMP,
    registration_closing_time TIMESTAMP,
    capacity INTEGER,
    visibility_status VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    organizer_id BIGINT NOT NULL REFERENCES users(id),
    venue_id BIGINT NOT NULL REFERENCES venues(id)
);
