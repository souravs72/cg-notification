-- Migration: Skip metrics update when message_logs.site_id is NULL
-- PREREQUISITE: migration-make-site-id-nullable.sql (site_id must be nullable first)
-- Dashboard messages are created without site_id; site_metrics_daily.site_id is NOT NULL,
-- so we must not run the metrics insert for those rows or the trigger would fail.

CREATE OR REPLACE FUNCTION update_daily_metrics()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.site_id IS NULL THEN
        RETURN NEW;
    END IF;

    INSERT INTO site_metrics_daily (site_id, metric_date, channel, total_sent, total_success, total_failed)
    VALUES (
        NEW.site_id,
        DATE(NEW.created_at),
        NEW.channel::text,
        1,
        CASE WHEN NEW.status = 'DELIVERED' THEN 1 ELSE 0 END,
        CASE WHEN NEW.status IN ('FAILED', 'BOUNCED', 'REJECTED') THEN 1 ELSE 0 END
    )
    ON CONFLICT (site_id, metric_date, channel)
    DO UPDATE SET
        total_sent = site_metrics_daily.total_sent + 1,
        total_success = site_metrics_daily.total_success +
            CASE WHEN NEW.status = 'DELIVERED' THEN 1 ELSE 0 END,
        total_failed = site_metrics_daily.total_failed +
            CASE WHEN NEW.status IN ('FAILED', 'BOUNCED', 'REJECTED') THEN 1 ELSE 0 END,
        updated_at = CURRENT_TIMESTAMP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

