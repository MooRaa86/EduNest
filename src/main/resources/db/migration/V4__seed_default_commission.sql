-- V4__seed_default_commission.sql
-- EB-47 / P3-05: Commission default seeding (MSH-05, Decision D-17)
-- Seeds initial commission rate if no commission record exists.
-- Configurable via Flyway placeholder ${defaultCommissionRate} (default: 10.0).

INSERT INTO commission (rate, created_at, updated_at, created_by, updated_by)
SELECT ${defaultCommissionRate}, NOW(), NOW(), 'SYSTEM', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM commission);
