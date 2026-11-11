-- EB-53 / P4-04 (D-08): quiz close deadline + submission late flag.
-- Additive, nullable columns only: legacy rows stay compatible (quiz.close_at NULL =
-- no automatic close deadline; quiz_submission.is_late NULL = historical row of
-- unknown lateness, never backfilled).

ALTER TABLE quiz
    ADD COLUMN close_at timestamp(6);

ALTER TABLE quiz_submission
    ADD COLUMN is_late boolean;
