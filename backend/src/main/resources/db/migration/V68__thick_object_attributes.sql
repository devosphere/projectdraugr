-- V68: the additive thick-object layer (DR-0022 Layer 4).
--
-- Phase 0's richness lived in the ITEM: every crafted thing was a bespoke, evolving entity with
-- capabilities, dimensions, and a dated modification log (the "Utility Belt Revision III with a stone
-- hammer holder, added on day 8"). Phase 1's item_instance is a thin instance of a fixed catalogue. This
-- adds that thickness back WITHOUT touching the catalogue or mass balance: two additive side-tables of
-- semantic metadata on a world_object. Nothing reads these to gate physics; they only describe.
--
--   * object_attribute   — key/value features of one specific object (handle=true, length_cm=28, capability=carry).
--                          The runtime Architect may populate these for an authored item; REFINE and crafts
--                          may set them; examination surfaces them. Never an input to the physics gate.
--   * object_modification — a dated log of how one object changed over its life (refined, a holder added).
--                          Complements object_transition (which logs lifecycle events) with human-readable
--                          improvement notes the chronicle can read back when inspecting the thing.

CREATE TABLE object_attribute (
    object_id  UUID         NOT NULL REFERENCES world_object(id),
    attr_key   VARCHAR(40)  NOT NULL,
    attr_value VARCHAR(200) NOT NULL,
    PRIMARY KEY (object_id, attr_key)
);

CREATE TABLE object_modification (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    object_id   UUID         NOT NULL REFERENCES world_object(id),
    occurred_at TIMESTAMPTZ  NOT NULL,
    note        VARCHAR(200) NOT NULL
);

CREATE INDEX object_modification_object_idx ON object_modification(object_id, occurred_at DESC);
