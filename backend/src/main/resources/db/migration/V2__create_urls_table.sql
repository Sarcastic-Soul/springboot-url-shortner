CREATE TABLE urls
(
    id UUID PRIMARY KEY,

    short_code VARCHAR(10) NOT NULL UNIQUE,

    original_url TEXT NOT NULL,

    title VARCHAR(255),

    description TEXT,

    click_count BIGINT NOT NULL DEFAULT 0,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    expires_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL,

    updated_at TIMESTAMP NOT NULL,

    user_id UUID NOT NULL,

    CONSTRAINT fk_urls_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_urls_short_code
ON urls(short_code);

CREATE INDEX idx_urls_user
ON urls(user_id);