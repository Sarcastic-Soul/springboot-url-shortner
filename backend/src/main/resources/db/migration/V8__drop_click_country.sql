-- GeoIP never resolved: app.geoip.database-path was empty in every profile, so the reader was
-- always null and every row was written as 'Unknown'. The feature has been removed rather than
-- left as a column the UI displays and nothing populates.
ALTER TABLE url_clicks
    DROP COLUMN IF EXISTS country;
