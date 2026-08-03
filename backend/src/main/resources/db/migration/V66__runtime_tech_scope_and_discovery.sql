-- V66: the scope + ledger that let a chronicle carry runtime-invented tech (DR-0021, stage C2).
--
-- A mechanic the Runtime Architect authors at play time is DATA scoped to the chronicle that
-- discovered it — never schema, never canon. The schema itself stays global and monotonic
-- (DR-0013): we add one nullable owner column, and resolution reads canonical rows (owner NULL,
-- visible to all) plus the current chronicle's own. Nothing changes for existing play — every
-- existing row has a NULL owner and is therefore canonical.

ALTER TABLE material_process ADD COLUMN discovered_by_chronicle_id UUID NULL REFERENCES chronicle(id);
ALTER TABLE item_definition  ADD COLUMN discovered_by_chronicle_id UUID NULL REFERENCES chronicle(id);

-- The read filter every mechanic lookup uses: canonical OR mine. Partial index on the private rows,
-- which are the rare ones.
CREATE INDEX idx_material_process_scope ON material_process (discovered_by_chronicle_id) WHERE discovered_by_chronicle_id IS NOT NULL;
CREATE INDEX idx_item_definition_scope  ON item_definition  (discovered_by_chronicle_id) WHERE discovered_by_chronicle_id IS NOT NULL;

-- The discovery ledger: the source of truth for the review pipeline (Overseer -> human -> canon patch).
-- Derived/ops data, NOT immutable player history — a human may mark it promoted/resolved.
CREATE TABLE chronicle_tech_discovery (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chronicle_id       UUID NOT NULL REFERENCES chronicle(id),
    discovered_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    procedure_text     TEXT NOT NULL,                 -- what the player actually attempted
    process_key        VARCHAR(100) NULL,             -- the scoped process authored, if one was
    gate_result        TEXT NULL,                     -- deterministic physics-gate verdict (mass balance/reachability)
    qa_verdict         TEXT NULL,                     -- QA critic verdict + reasons
    model              TEXT NULL,                     -- the architect model that drafted it
    promoted           BOOLEAN NOT NULL DEFAULT FALSE,-- accepted toward canon by a human
    promoted_migration TEXT NULL,                     -- the V*.sql it became, once shipped
    resolved           BOOLEAN NOT NULL DEFAULT FALSE -- triaged (promoted or rejected)
);

-- The review queue is "what's discovered and still open," newest first.
CREATE INDEX idx_tech_discovery_open ON chronicle_tech_discovery (discovered_at DESC) WHERE resolved = FALSE;
