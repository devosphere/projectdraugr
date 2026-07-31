-- V56: Record what the foundation could not resolve, and why.
--
-- Routing hardening (V54, V55) made gaps honest. It did not make them visible. When
-- an action fails to resolve, nothing anywhere records that it happened, so the only
-- way to learn what the world is missing has been to simulate procedures by hand and
-- read the results -- which is how every gap so far was found, and is not a system.
--
-- This is the instrument the coverage work needs. It answers the question that
-- decides where effort goes: when an action does not resolve, is it because the
-- WORDS are missing or because the MECHANIC is missing? Those have completely
-- different fixes -- a category term versus a whole process definition -- and
-- guessing which one dominates is exactly the kind of re-derivation that costs more
-- than the fix.
--
-- Deliberately not a log of every action. It is a frequency-ranked backlog: repeats
-- increment a counter rather than adding rows, so the table stays small and sorts
-- itself by how much each gap is actually costing.

CREATE TABLE routing_miss (
    -- md5 of the normalised text, so the key has no length limit and repeats collapse.
    text_hash           CHAR(32)     PRIMARY KEY,
    normalised_text     TEXT         NOT NULL,
    -- The raw text as one player actually typed it, kept for reading the backlog.
    sample_text         TEXT         NOT NULL,
    -- NULL means the vocabulary recognised nothing in the sentence at all.
    classified_category VARCHAR(20)  REFERENCES activity_category(category_key),
    -- How far the best candidate got before a gate rejected it. This is the whole
    -- diagnostic value of the table.
    --   NONE     - no process even shares the classified category
    --   CATEGORY - a process shares the category, but no keyword matched
    --   KEYWORD  - category and keyword both matched, but no subject term did
    furthest_gate       VARCHAR(20)  NOT NULL CHECK (furthest_gate IN ('NONE','CATEGORY','KEYWORD')),
    -- The process that got furthest, when one did. Usually the right place to look.
    near_process_key    VARCHAR(60)  REFERENCES material_process(process_key),
    hit_count           INTEGER      NOT NULL DEFAULT 1 CHECK (hit_count > 0),
    first_seen          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX routing_miss_frequency_idx ON routing_miss(hit_count DESC);
CREATE INDEX routing_miss_gate_idx ON routing_miss(furthest_gate);

-- The backlog, classified by what would actually fix each entry. Ordered by cost:
-- a miss seen forty times is worth forty times the attention of one seen once.
CREATE VIEW routing_miss_backlog AS
SELECT
    m.sample_text,
    m.normalised_text,
    m.classified_category,
    m.near_process_key,
    m.hit_count,
    m.last_seen,
    CASE
        -- Nothing in the sentence was recognised. Cheapest possible fix: terms.
        WHEN m.classified_category IS NULL          THEN 'VOCABULARY'
        -- Classified fine, but no process exists in that category to do this.
        -- This is the expensive kind and the one bulk generation is for.
        WHEN m.furthest_gate = 'NONE'               THEN 'MECHANIC'
        -- The right process is there and knows the category, but not this phrasing.
        WHEN m.furthest_gate = 'CATEGORY'           THEN 'KEYWORD'
        -- Right process, right words, wrong material -- either a missing subject term
        -- or the player genuinely meant a material this process does not handle.
        ELSE 'SUBJECT'
    END AS gap_kind
FROM routing_miss m;

-- Words the world has never heard. Every one is a candidate category term, and the
-- ranking says which are worth an Architect call to classify -- one call per novel
-- verb ever, against a per-action call forever.
--
-- A word already carried by category_term or process_subject is known vocabulary and
-- is not the reason anything missed, so it is excluded.
CREATE VIEW routing_unknown_term AS
SELECT
    w.word,
    SUM(m.hit_count)::BIGINT AS occurrences,
    COUNT(*)::BIGINT         AS distinct_actions
FROM routing_miss m
CROSS JOIN LATERAL regexp_split_to_table(m.normalised_text, ' ') AS w(word)
WHERE length(w.word) >= 3
  AND w.word NOT IN ('the','and','for','you','your','its','out','off','into','onto','with',
                     'from','that','this','then','than','some','any','all','one','two',
                     'get','got','put','use','using','make','made','not','but','are','was',
                     'have','has','had','can','will','would','should','about','over','under',
                     'down','back','through','around','while','when','where','what','how')
  AND NOT EXISTS (SELECT 1 FROM category_term ct WHERE ct.term = w.word)
  AND NOT EXISTS (SELECT 1 FROM process_subject s WHERE s.subject_term = w.word)
GROUP BY w.word;

INSERT INTO domain_registry (domain_key, display_name, description, introduced_in_migration, origin)
VALUES ('routing_coverage','Routing Coverage','A frequency-ranked backlog of actions the foundation could not resolve, classified by whether the gap is vocabulary or mechanic, so coverage work is directed by measurement rather than by hand-run simulations.','V56','PREBUILT')
ON CONFLICT (domain_key) DO NOTHING;
