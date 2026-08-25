-- V166: finite fish stocks — water is not bottomless either (EPIC #180 sibling / #181 finite depletion; #36 fishing).
--
-- Minerals now run out (V165) and woodland now runs out and grows back (#200). Fishing was the last bottomless
-- faucet: fish() read the aquatic species a biome holds and yielded fish forever, with no memory of how hard a
-- stretch of water had been worked. This records how many fish a chunk's water still holds, drawn down by each catch
-- and slowly restocked over time as fish breed back — so a spot fished relentlessly thins and, given rest, recovers.
--
-- Recorded lazily at full the first time a stretch is fished (no world-generation change), and generous, so ordinary
-- angling never notices the ceiling; only sustained over-fishing of one spot exhausts it. Fish, unlike a mineral
-- seam, come back — the restock is handled in PhysicalItemService/WildlifeEncounterService from last_fished_at.

CREATE TABLE fish_stock (
    chunk_id        UUID         NOT NULL PRIMARY KEY REFERENCES world_chunk(id),
    remaining_units INTEGER      NOT NULL CHECK (remaining_units >= 0),
    last_fished_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE fish_stock IS
    'Per-chunk remaining fish (#181/#36). Seeded lazily at full on first catch by WildlifeEncounterService.fish, drawn down per catch, and restocked over time from last_fished_at; zero means fished out here until it recovers.';
