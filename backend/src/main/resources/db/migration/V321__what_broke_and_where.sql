-- #83 — what broke, and where: the developer-only regression record.
--
-- #83's last comment records what stays unbuilt: "the developer-only regression record capturing failure query,
-- SQLSTATE, migration version, action ledger id and object ids is not implemented; failures currently surface as
-- test output, not a structured record." system_error_log (V64) already catches every hard fault in its own
-- transaction and keeps it from the player — but what it files is a class name, a message and a URL. To reproduce
-- a fault a developer needs what the ticket lists, and a log line is where they would otherwise have to dig it out
-- of free text, if the driver happened to include it at all.
--
-- Each column is filled best-effort by SystemErrorRecorder and may be NULL — recording must never become the error:
--   sql_state            the PostgreSQL SQLSTATE from the SQLException in the cause chain (42P01, 23505, ...)
--   failing_sql          the statement Spring was running, where the exception carries it
--   schema_version       the newest successful Flyway migration when the fault happened
--   chronicle_id         the living Chronicle at the time — no FK, a log must outlive what it describes
--   action_text          what the player submitted, when the fault came out of an action
--   action_idempotency_key  the key that action was submitted under; the ledger row itself rolled back with the fault
--   object_ids           every UUID the error names (a violated key, a missing row)

ALTER TABLE system_error_log
    ADD COLUMN sql_state              TEXT,
    ADD COLUMN failing_sql            TEXT,
    ADD COLUMN schema_version         TEXT,
    ADD COLUMN chronicle_id           UUID,
    ADD COLUMN action_text            TEXT,
    ADD COLUMN action_idempotency_key UUID,
    ADD COLUMN object_ids             UUID[];

COMMENT ON COLUMN system_error_log.sql_state IS 'SQLSTATE of the failing statement, when the fault was a database one (#83).';
COMMENT ON COLUMN system_error_log.failing_sql IS 'The statement that failed, when the exception carried it (#83).';
COMMENT ON COLUMN system_error_log.schema_version IS 'Newest successful Flyway migration at the time of the fault (#83).';
COMMENT ON COLUMN system_error_log.action_idempotency_key IS 'The submitted action''s idempotency key; its ledger row rolled back with the fault (#83).';
COMMENT ON COLUMN system_error_log.object_ids IS 'Every UUID named in the error message (#83).';

-- The triage question after "what is open" is "has this broken before": group by the SQLSTATE and the statement.
CREATE INDEX idx_system_error_log_sql_state ON system_error_log (sql_state, occurred_at DESC) WHERE sql_state IS NOT NULL;
