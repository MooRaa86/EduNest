-- EB-51: row-level optimistic versions. Existing business values are unchanged.
ALTER TABLE badges ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE total_points ADD COLUMN version bigint NOT NULL DEFAULT 0;
