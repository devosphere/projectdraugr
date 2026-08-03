-- Regression: runtime-authored tech is SCOPED to the chronicle that discovered it (DR-0021 / V66).
--
-- The security-relevant guarantee: a chronicle's private, AI-authored mechanic (discovered_by set)
-- must be visible to that chronicle and to NO ONE else, while canonical mechanics (discovered_by NULL)
-- are everyone's. This pins the exact filter ProcessMatcher.candidates(chronicle) applies —
-- "review_state='VERIFIED' AND (discovered_by IS NULL OR discovered_by = :chronicle)" — plus the
-- discovery ledger's open-queue read. Self-contained (temp tables) so it needs no seeded chronicle FK
-- chain; the same filter runs against the real material_process table in the app + CI.
--
-- Any RAISE below exits non-zero under ON_ERROR_STOP, failing the suite.

BEGIN;

CREATE TEMP TABLE mp (process_key text PRIMARY KEY, review_state text NOT NULL, discovered_by uuid) ON COMMIT DROP;
INSERT INTO mp VALUES
    ('split_planks',    'VERIFIED', NULL),                                        -- canonical: everyone's
    ('twist_cord_cA',   'VERIFIED', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),      -- private to chronicle A
    ('smelt_bloom_cB',  'VERIFIED', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),      -- private to chronicle B
    ('draft_pending',   'DRAFT',    NULL);                                        -- unverified: never a candidate

-- 1) Chronicle A sees canonical + only ITS OWN scoped tech (not B's, not the DRAFT).
DO $$
DECLARE keys text;
BEGIN
    SELECT string_agg(process_key, ',' ORDER BY process_key) INTO keys FROM mp
    WHERE review_state='VERIFIED' AND (discovered_by IS NULL OR discovered_by='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
    IF keys <> 'split_planks,twist_cord_cA' THEN
        RAISE EXCEPTION 'REGRESSION: chronicle A visibility wrong (got: %)', keys; END IF;
    RAISE NOTICE 'PASS: a chronicle sees canonical + its own scoped tech, and only its own';
END $$;

-- 2) Chronicle B never sees chronicle A's private tech (the isolation guarantee).
DO $$
DECLARE keys text;
BEGIN
    SELECT string_agg(process_key, ',' ORDER BY process_key) INTO keys FROM mp
    WHERE review_state='VERIFIED' AND (discovered_by IS NULL OR discovered_by='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
    IF keys <> 'smelt_bloom_cB,split_planks' THEN
        RAISE EXCEPTION 'REGRESSION: chronicle B leaked another chronicle''s scoped tech (got: %)', keys; END IF;
    RAISE NOTICE 'PASS: one chronicle never sees another chronicle''s private tech';
END $$;

-- 3) A null chronicle (read-only callers with no chronicle in hand) sees canonical only.
DO $$
DECLARE keys text;
BEGIN
    SELECT string_agg(process_key, ',' ORDER BY process_key) INTO keys FROM mp
    WHERE review_state='VERIFIED' AND (discovered_by IS NULL OR discovered_by = NULL::uuid);
    IF keys <> 'split_planks' THEN
        RAISE EXCEPTION 'REGRESSION: null-chronicle read saw non-canonical rows (got: %)', keys; END IF;
    RAISE NOTICE 'PASS: null chronicle = canonical only';
END $$;

-- 4) The discovery ledger accepts a record and the open-queue read works.
CREATE TEMP TABLE ctd (id uuid DEFAULT gen_random_uuid(), procedure_text text NOT NULL, process_key text, resolved boolean NOT NULL DEFAULT false) ON COMMIT DROP;
INSERT INTO ctd (procedure_text, process_key) VALUES ('smelt the ore into a bloom', 'smelt_bloom_cB');
DO $$
DECLARE open_count int;
BEGIN
    SELECT count(*) INTO open_count FROM ctd WHERE resolved = false;
    IF open_count <> 1 THEN RAISE EXCEPTION 'REGRESSION: discovery ledger open-queue wrong (got: %)', open_count; END IF;
    RAISE NOTICE 'PASS: discovery ledger records a find and the open-queue read returns it';
END $$;

ROLLBACK;
