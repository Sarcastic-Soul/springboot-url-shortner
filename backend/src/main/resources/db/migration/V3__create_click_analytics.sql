CREATE TABLE url_clicks (

    id UUID PRIMARY KEY,

    url_id UUID NOT NULL REFERENCES urls(id) ON DELETE CASCADE,

    clicked_at TIMESTAMP NOT NULL DEFAULT now(),

    ip_address VARCHAR(64),

    user_agent TEXT,

    referer TEXT

);

CREATE INDEX idx_click_url
ON url_clicks(url_id);

CREATE INDEX idx_click_time
ON url_clicks(clicked_at);
