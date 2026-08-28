-- V192 — draft training: a working beast hardens to the work (EPIC #100 / #101 training & working-animal progression).
-- V190 gave a beast fatigue — it tires as it works. But a green beast and a seasoned one are not the same: an animal
-- worked day after day hardens to the draught, learning the pull and building the wind and muscle for it, so it tires
-- less for the same load. This gives each bond a draft-conditioning that rises slowly as the beast is worked (in
-- PhysicalItemService.workDraftBeasts, alongside the fatigue it accrues), and eases the bite of fatigue on its haul:
-- a fully-seasoned beast feels only half the fatigue a green one does, so it keeps pulling where a green beast would
-- already have flagged. Conditioning never lifts a fresh beast above its base haul — it is endurance, not strength;
-- its worth shows only when the beast is tired. The slow, earned progression that turns a tamed animal into a working one.
ALTER TABLE wildlife_bond ADD COLUMN draft_conditioning SMALLINT NOT NULL DEFAULT 0 CHECK (draft_conditioning BETWEEN 0 AND 100);
