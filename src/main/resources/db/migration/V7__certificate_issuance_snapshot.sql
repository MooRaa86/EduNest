ALTER TABLE certificates ADD COLUMN IF NOT EXISTS points_at_issuance integer;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS cohort_size_at_issuance bigint;
ALTER TABLE user_notification ADD COLUMN IF NOT EXISTS certificate_issuance_id uuid;
ALTER TABLE user_notification ADD COLUMN IF NOT EXISTS certificate_recipient_email varchar(255);
CREATE INDEX IF NOT EXISTS idx_certificate_notification_issuance ON user_notification(certificate_issuance_id, id);
