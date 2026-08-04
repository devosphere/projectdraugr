-- V70: many named zones per chunk (DR-0022 Layer 4b, F8).
--
-- A settlement is a cluster of named spots the chronicle names and remembers — a Tool Shed, a Wood Store, a
-- Drinking Area, a Sleeping Area — not one name per 10 km chunk. Relax the key so a chronicle may name several
-- distinct zones within one chunk (unique by name), and track which named zone the chronicle is currently at.
--
-- IMPORTANT (DR-0022, "Principles settled during Layer 4b"): current_zone is a LABEL for narration and for
-- "go to <zone>" movement WITHIN the chunk. Reachability stays CHUNK-WIDE and is NOT scoped down to the zone —
-- a chronicle who knows their settlement fetches from any of its zones as part of an act. Zones are
-- organization/navigation on top of unchanged chunk-wide reach, never a reachability restriction.

ALTER TABLE chronicle_named_location DROP CONSTRAINT chronicle_named_location_pkey;
ALTER TABLE chronicle_named_location ADD PRIMARY KEY (chronicle_id, chunk_id, name);

ALTER TABLE chronicle ADD COLUMN current_zone TEXT NULL;
