-- Supports AnonymousUrlCleanupScheduler.findAllByUserIsNullAndExpiresAtBefore,
-- which currently sequential-scans the urls table on every replica.
CREATE INDEX IF NOT EXISTS idx_urls_anon_expiry
    ON urls (expires_at)
    WHERE user_id IS NULL;

-- Supports UrlClickRepository.findTop20ByUrlOrderByClickedAtDesc.
-- The existing idx_click_url alone forces a sort for the ORDER BY.
CREATE INDEX IF NOT EXISTS idx_click_url_time
    ON url_clicks (url_id, clicked_at DESC);
