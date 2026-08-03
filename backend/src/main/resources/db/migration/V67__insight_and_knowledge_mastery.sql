-- V67: two more capability dimensions for the examination verbs (GitHub #25).
--
-- Inspect/Analyze/Investigate reveal a subject at a depth set by the chronicle's mastery. Perception is
-- the existing attention_resilience; this adds INSIGHT (understanding how a thing works and what it is
-- for) and KNOWLEDGE (drawing origin/provenance and critical inferences). Per-chronicle and hidden, like
-- every other capability — they die with the chronicle and grow slowly with practice.

ALTER TABLE chronicle_capability_adaptation
    ADD COLUMN insight_familiarity   REAL NOT NULL DEFAULT 0 CHECK (insight_familiarity   BETWEEN 0 AND 1),
    ADD COLUMN knowledge_familiarity REAL NOT NULL DEFAULT 0 CHECK (knowledge_familiarity BETWEEN 0 AND 1);
