-- Language of the texts the backend sends a user (SRS section 7): Russian by default, Kazakh or English.
-- Existing accounts get Russian; old app versions ignore the column.
ALTER TABLE users ADD COLUMN language character varying(2) NOT NULL DEFAULT 'RU';
ALTER TABLE users ADD CONSTRAINT users_language_check CHECK (language IN ('RU', 'KK', 'EN'));
