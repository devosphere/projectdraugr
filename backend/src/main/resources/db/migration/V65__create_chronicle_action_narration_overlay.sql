-- V65: A narration overlay, so the AI-enriched prose the player saw live is durable —
-- readable in history, in the journey archive, and in the PDF export — WITHOUT ever
-- writing back to the append-only chronicle_action row.
--
-- chronicle_action is immutable history (prevent_chronicle_action_mutation blocks any
-- UPDATE), and its narration column is the deterministic source of truth. The Simulation
-- Agent's atmospheric sentence — and the death coda — are appended to what's shown to the
-- player at resolution time; previously they were live-only and vanished on reload. This
-- table stores that final displayed prose in a SEPARATE row keyed by action_id, which the
-- read paths LEFT JOIN and COALESCE over the base narration. The base row stays untouched,
-- so immutability and the token-safety ordering both hold: the overlay INSERT is the last
-- thing an action does, after the single model call, and targets a table with no mutation
-- trigger and only a satisfied FK — it cannot raise the class of error that motivated all
-- of this.
--
-- model records which model produced the narration (null when only the deterministic death
-- coda differs, no AI). This is deliberately kept so a later review can judge narration
-- quality per model — e.g. whether Haiku is sufficient or a stronger narrator is worth the
-- cost. It is an operations/derived overlay, NOT player history: intentionally mutable, so a
-- narration could be regenerated, and it is never the source of truth for what happened.

CREATE TABLE chronicle_action_narration (
    action_id  UUID PRIMARY KEY REFERENCES chronicle_action(id),
    narration  TEXT NOT NULL,
    model      TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The model-quality review query is "show me the AI-authored narrations, by model."
CREATE INDEX idx_chronicle_action_narration_model ON chronicle_action_narration (model) WHERE model IS NOT NULL;
