package com.devosphere.draugr.world.genesis;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * River carving is world generation, not database work, so it is proved here without Docker (#156). RIVER_BANK was
 * a biome sixteen places in the codebase branched on and the generator never produced; these are the properties
 * that make what it now produces a river rather than a scatter of wet tiles.
 */
class WorldRiverCarvingTest {

    private WorldGenesisService generator() { return new WorldGenesisService(null, "target/test-world-exports"); }

    private static int x(long key) { return (int) (key >> 32); }
    private static int y(long key) { return (int) (key & 0xFFFFFFFFL); }

    @Test
    void theDefaultWorldIsCutWithRivers() {
        WorldGenesisService.GenesisRequest request = WorldGenesisService.GenesisRequest.mvpDefault();
        Set<Long> cells = generator().riverCells(request.widthChunks(), request.heightChunks(), request.seed());
        assertFalse(cells.isEmpty(), "the canonical world must have running water in it");
        assertTrue(cells.size() < request.widthChunks() * request.heightChunks() / 4,
            "rivers are channels through the land, not a flooded map — got " + cells.size() + " cells");
    }

    @Test
    void everyRiverCellJoinsTheChannelItBelongsTo() {
        WorldGenesisService.GenesisRequest request = WorldGenesisService.GenesisRequest.mvpDefault();
        Set<Long> cells = generator().riverCells(request.widthChunks(), request.heightChunks(), request.seed());
        for (long cell : cells) {
            boolean joined = false;
            for (int dy = -1; dy <= 1 && !joined; dy++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                if (cells.contains(((long) (x(cell) + dx) << 32) | ((y(cell) + dy) & 0xFFFFFFFFL))) { joined = true; break; }
            }
            assertTrue(joined, "river cell (" + x(cell) + "," + y(cell) + ") stands alone; water runs, it does not appear in puddles");
        }
    }

    @Test
    void carvingIsDeterministicForASeed() {
        WorldGenesisService.GenesisRequest request = WorldGenesisService.GenesisRequest.mvpDefault();
        Set<Long> first = generator().riverCells(request.widthChunks(), request.heightChunks(), request.seed());
        Set<Long> again = generator().riverCells(request.widthChunks(), request.heightChunks(), request.seed());
        assertEquals(first, again, "the same seed must cut the same rivers, in a fresh generator as in a warm one");
    }

    @Test
    void aDifferentSeedCutsDifferentRivers() {
        WorldGenesisService.GenesisRequest request = WorldGenesisService.GenesisRequest.mvpDefault();
        WorldGenesisService shared = generator();
        Set<Long> canonical = shared.riverCells(request.widthChunks(), request.heightChunks(), request.seed());
        Set<Long> other = shared.riverCells(request.widthChunks(), request.heightChunks(), request.seed() + 7919L);
        assertFalse(other.isEmpty(), "another seed must still find high ground to drain");
        assertFalse(canonical.equals(other), "the hydrology must follow the seed's terrain, not be stamped on every world alike");
    }
}
