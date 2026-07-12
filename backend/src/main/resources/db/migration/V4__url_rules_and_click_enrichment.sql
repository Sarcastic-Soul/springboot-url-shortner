ALTER TABLE urls
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE urls
    ADD COLUMN tags TEXT,
    ADD COLUMN password_hash TEXT,
    ADD COLUMN max_clicks BIGINT;

ALTER TABLE url_clicks
    ADD COLUMN ip_hash TEXT,
    ADD COLUMN country VARCHAR(8),
    ADD COLUMN device VARCHAR(100),
    ADD COLUMN browser VARCHAR(100),
    ADD COLUMN os VARCHAR(100);
