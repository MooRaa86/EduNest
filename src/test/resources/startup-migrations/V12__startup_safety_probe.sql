-- Test-only sentinel: normal application startup must never execute this migration.
CREATE TABLE public.flyway_startup_safety_probe (id integer);
