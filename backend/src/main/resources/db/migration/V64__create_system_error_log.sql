-- V64: A durable tracker for hard runtime errors, so a bug can't hide dormant.
--
-- This exists because of a real one: an AI-narration path issued an illegal UPDATE
-- against the append-only chronicle_action table. The immutability trigger caught it
-- and the transaction rolled back cleanly, but the failure was invisible until a live
-- playthrough hit it — and on the old ordering it fired AFTER a paid model call, so it
-- burned a token every time. This table records every hard error the global handler
-- catches (persistence failures and otherwise-unhandled exceptions), so such faults
-- surface for triage instead of staying silent until someone stumbles on them.
--
-- ai_was_live records whether the AI layer was switched on when the error occurred: a
-- proxy for "could a model call have been wasted here." Under the current ordering the
-- Simulation Agent's call is the last thing an action does, so a hard error precedes it
-- and no token is spent — but this flag lets us confirm that holds, and would flag any
-- future regression that reintroduces a spend-then-fail path.
--
-- Deliberately NOT immutable: this is an operations log, not player history. Rows are
-- inserted by the exception handler and may be marked triaged/resolved by a human.

CREATE TABLE system_error_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    http_status   INT NOT NULL,
    error_class   TEXT NOT NULL,
    error_message TEXT NULL,
    request_path  TEXT NULL,
    ai_was_live   BOOLEAN NOT NULL DEFAULT FALSE,
    resolved      BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_note TEXT NULL
);

-- The triage query is "what's broken and still open," newest first.
CREATE INDEX idx_system_error_log_open ON system_error_log (occurred_at DESC) WHERE resolved = FALSE;
