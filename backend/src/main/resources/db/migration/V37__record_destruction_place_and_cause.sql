-- A destroyed object is never removed from the world's memory. It keeps its row
-- and its identity; only its status becomes DESTROYED, and the world records WHERE
-- it was destroyed and HOW. current_location_id/current_owner_id stay null (it is
-- no longer in the active world), while these columns preserve the historical fact.
ALTER TABLE world_object
    ADD COLUMN destroyed_location_id UUID NULL REFERENCES world_chunk(id),
    ADD COLUMN destroyed_cause VARCHAR(60) NULL;

-- Any object already destroyed before this migration has an unrecoverable place of
-- death, but its cause is at least recorded so the invariant "destroyed objects
-- record how they ended" holds going forward.
UPDATE world_object SET destroyed_cause = 'UNKNOWN_LEGACY'
    WHERE lifecycle_state = 'DESTROYED' AND destroyed_cause IS NULL;
