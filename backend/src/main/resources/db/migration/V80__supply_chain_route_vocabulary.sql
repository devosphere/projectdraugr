-- V80: route-family vocabulary for the supply chains (M1 #62, EPIC #54).
--
-- #62 asks that every supply-chain route verb classify, so alternate wording routes to the three-axis matcher
-- (resolving where a process exists, else recording a CLASSIFIED miss — never papered over with a generic
-- success). Most route verbs are already in category_term; these few were missing. Categories agree with the
-- processes that already carry the words (hang -> the drying/smoking processes; burn -> charring), and the
-- build verbs go to CONSTRUCT so a fence/bridge request records a real gap until those projects exist.
--
-- Deliberately NOT added: drag/stack/cover (intent-handled — DISENGAGE/CLOSE_CONTAINER — or #67-deferred
-- mechanics), and line/prepare (too generic — they would pull unrelated processing to the wrong category, the
-- same hazard V54 called out for "make"/"process").

INSERT INTO category_term (category_key, term, weight) VALUES
    ('PROCESS',   'burn',   2),
    ('PROCESS',   'hang',   1),
    ('PROCESS',   'slake',  2),
    ('CONSTRUCT', 'fence',  2),
    ('CONSTRUCT', 'bridge', 2)
ON CONFLICT (category_key, term) DO NOTHING;
