-- #38 — a step whose words name two pieces of work.
--
-- ProcessMatcher runs the process whose keyword is longest. When two processes answer to the same words — "carve a
-- bowl" is a keyword of both carve_soapstone_bowl and carve_wooden_bowl, "make a yoke" of both yokes, "stitch" of six
-- leather pieces — it took the lexically first key, silently. A Chronicle holding wood and no soapstone who wrote
-- "carve a bowl" was told they had no soapstone. Forty such pairs share a keyword and a subject in the catalogue.
--
-- The play path now narrows a tie by what the text itself names (an output's own words), then by what is in reach,
-- and only when that still leaves more than one does it ask, naming the choices, and record the ambiguity here. This
-- migration lets the backlog hold that record: the gate gains AMBIGUOUS, the row keeps which processes tied, and the
-- backlog classifies it as its own kind of gap — fixed by a sharper keyword, not by vocabulary or a mechanic.

ALTER TABLE routing_miss DROP CONSTRAINT routing_miss_furthest_gate_check;
ALTER TABLE routing_miss ADD CONSTRAINT routing_miss_furthest_gate_check
    CHECK (furthest_gate IN ('NONE','CATEGORY','KEYWORD','AMBIGUOUS'));

ALTER TABLE routing_miss ADD COLUMN tied_process_keys TEXT[];
COMMENT ON COLUMN routing_miss.tied_process_keys IS
  'For an AMBIGUOUS miss: every process the words fitted equally, which neither the text nor what was in reach could choose between (#38).';

CREATE OR REPLACE VIEW routing_miss_backlog AS
SELECT
    m.sample_text,
    m.normalised_text,
    m.classified_category,
    m.near_process_key,
    m.hit_count,
    m.last_seen,
    CASE
        WHEN m.classified_category IS NULL          THEN 'VOCABULARY'
        WHEN m.furthest_gate = 'NONE'               THEN 'MECHANIC'
        WHEN m.furthest_gate = 'CATEGORY'           THEN 'KEYWORD'
        -- The words fit two processes equally. Fixed by giving one a sharper keyword, or by the player naming which.
        WHEN m.furthest_gate = 'AMBIGUOUS'          THEN 'AMBIGUITY'
        ELSE 'SUBJECT'
    END AS gap_kind
FROM routing_miss m;

DO $$
BEGIN
  IF position('''AMBIGUOUS''' IN (SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'routing_miss_furthest_gate_check')) = 0 THEN
    RAISE EXCEPTION 'V318: the gate constraint must accept AMBIGUOUS';
  END IF;
  IF position('AMBIGUITY' IN pg_get_viewdef('routing_miss_backlog'::regclass)) = 0 THEN
    RAISE EXCEPTION 'V318: the backlog must classify an ambiguity';
  END IF;
END $$;
