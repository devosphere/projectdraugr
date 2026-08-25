-- V168: coppicing — a wood harvested without felling it (EPIC #200 forestry / #204 coppice & sustainable harvest).
--
-- Felling takes the whole tree and draws the stand down; a young regrowth then gives poor timber (#201). Coppicing is
-- the opposite discipline, and the oldest woodland craft there is: cut the rods and poles from a living stool and the
-- stool throws up fresh growth from its own roots, so the same ground yields a crop of rods every few seasons without
-- ever losing the wood. This records when a chunk's stools were last cut, so a stand can be coppiced again only once
-- it has thrown up new growth — a renewable harvest that rewards leaving the wood standing, the counterpart to the
-- clear-cutting penalty.

ALTER TABLE chunk_flora ADD COLUMN last_coppiced_at TIMESTAMPTZ;

COMMENT ON COLUMN chunk_flora.last_coppiced_at IS
    'When this stand was last coppiced (#204): the stools need a rest to throw up new rods before they can be cut again. Set by WildlifeEncounterService/PhysicalItemService.coppice; the tree count is never reduced — coppicing does not fell.';
