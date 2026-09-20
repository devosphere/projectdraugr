-- #121 — the one the group follows.
--
-- The ticket asks for ecological and group roles that are persistent state with real consequences: a pack leader,
-- a matriarch, a dominant bull. It also says what such a role must never be — a stronger enemy tier, or a rank that
-- respawns to keep an encounter going.
--
-- This is the smallest honest version of it, and it needs one column. A social group that hunts, holds ground or
-- stands its ground is doing it behind somebody; the animal that comes at a Chronicle is that one. Killing it
-- scatters the group, and a scattered group is in none of the states the raid and ambush rules read — so for as
-- long as it lasts, that pack takes no stock and lies in wait for nobody. After ten days another animal comes to
-- the front of it and the mark clears. Nothing is spawned to replace anyone.

ALTER TABLE wildlife_population
    ADD COLUMN leader_lost_at TIMESTAMPTZ;

COMMENT ON COLUMN wildlife_population.leader_lost_at IS
  '#121: when this group lost the animal it followed. While set, the daily cascade holds the group SCATTERED, which takes it out of every hunting and raiding rule; cleared after ten days, when another comes to the front.';
