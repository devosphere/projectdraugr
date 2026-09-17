package com.devosphere.draugr.world.genesis;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Materializes approved Atlas concentrations as UUID-backed physical features. */
@Service
public class WorldEcologyGenesisService {
    private final JdbcTemplate jdbc;
    private final WorldGenesisService worldGenesis;

    public WorldEcologyGenesisService(JdbcTemplate jdbc, WorldGenesisService worldGenesis) {
        this.jdbc = jdbc;
        this.worldGenesis = worldGenesis;
    }

    @Transactional
    public EcologySummary seed() {
        WorldGenesisService.GenesisSummary world = worldGenesis.current();
        if (world == null) throw new IllegalStateException("World Genesis must be approved before ecology can be seeded.");
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM ecology_site WHERE world_id = ?", Integer.class, world.worldId());
        if (existing != null && existing > 0) throw new IllegalStateException("Canonical ecology already exists; ecology genesis may run only once.");

        List<WorldGenesisService.PreviewMarker> markers = worldGenesis.markerPlan(new WorldGenesisService.GenesisRequest(world.seed(), world.widthChunks(), world.heightChunks()));
        for (WorldGenesisService.PreviewMarker marker : markers) {
            UUID chunkId = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id = ? AND grid_x = ? AND grid_y = ?", UUID.class, world.worldId(), marker.x(), marker.y());
            UUID siteId = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'ECOLOGY_SITE', ?, ?)", siteId, marker.label(), chunkId);
            jdbc.update("INSERT INTO ecology_site (id, world_id, chunk_id, site_category, site_kind, baseline_abundance) VALUES (?, ?, ?, ?, ?, ?)",
                    siteId, world.worldId(), chunkId, marker.category(), marker.label(), abundanceFor(marker.category()));
        }
        seedFlora(world.worldId());
        jdbc.update("INSERT INTO world_event (occurred_at, event_type, aggregate_id, payload) VALUES (now(), 'WORLD_ECOLOGY_SEEDED', ?, jsonb_build_object('siteCount', ?, 'source', 'approved-overseer-atlas'))", world.worldId(), markers.size());
        return new EcologySummary(world.worldId(), markers.size());
    }

    /**
     * The stands that were always supposed to be there (#224/#155).
     *
     * <p>{@code chunk_flora} is the finite-stand model: a patch of blackberry or nettle with a quantity that a
     * Chronicle draws down and a regrowth clock that brings it back. Every consumer of it was already built —
     * {@code ExaminationService} names what grows within reach, gathering reads the stand before falling back to a
     * bare biome guess, {@code WildlifeSimulationService} regrows, depletes and recolonises it — and
     * <b>genesis planted nothing at all</b>. Rows were only ever created lazily, the first time somebody felled or
     * harvested on that ground, so a fresh world had no plants in it anywhere: looking closely at a meadow named
     * nothing growing, and the whole stand model only began once a player had already taken something.
     *
     * <p>Three kinds a chunk, chosen from the catalogue's own {@code biome_affinity} so nothing grows where it
     * could not, and deterministic by chunk coordinate: the same world seeded twice has the same plants in the
     * same places, which is what makes the map worth learning. Stands are 3 to 6 — a patch you can work for a few
     * days, not an inexhaustible field — and TREES are deliberately left out: felling has its own natural-stand
     * rule and a seeded row would quietly override it.
     *
     * <p>Additive and idempotent: a stand already recorded on a chunk is left exactly as it is, including one a
     * player has already worked down, so this can run on a world that has been lived in.
     */
    @Transactional
    public int seedFlora(UUID worldId) {
        return jdbc.update(
            "INSERT INTO chunk_flora (chunk_id, flora_key, quantity, capacity, established_at) " +
            "SELECT c.id, f.flora_key, f.stand, f.stand, now() " +
            "  FROM world_chunk c " +
            "  JOIN LATERAL (" +
            "    SELECT fd.flora_key, " +
            "           3 + (('x' || substr(md5(c.grid_x::text || ':' || c.grid_y::text || ':' || fd.flora_key), 1, 4))::bit(16)::int % 4) AS stand " +
            "      FROM flora_definition fd " +
            "     WHERE fd.organism_type <> 'TREE' " +
            "       AND fd.biome_affinity ILIKE '%' || c.biome || '%' " +
            "     ORDER BY md5(c.grid_x::text || ':' || c.grid_y::text || ':' || fd.flora_key) " +
            "     LIMIT 3) f ON TRUE " +
            " WHERE c.world_id = ? " +
            "   AND NOT EXISTS (SELECT 1 FROM chunk_flora x WHERE x.chunk_id = c.id AND x.flora_key = f.flora_key)",
            worldId);
    }

    /**
     * Place any marker the approved plan carries that this world does not yet have, and nothing else.
     *
     * <p>{@link #seed()} may run only once, which is right — it is genesis. But it means a world generated before
     * a marker existed can never receive it, and #160's acceptance criteria are explicit that "the current pinned
     * world can receive the topology without regenerating or erasing history". A Chronicle has been living in
     * that world for the whole of this project; regenerating it is not on the table, and neither is leaving the
     * geological provinces unreachable in the only world anybody is actually playing.
     *
     * <p>Additive by construction. Placement comes from the same {@code markerPlan} genesis used, so a site
     * appears exactly where genesis would have put it and the Atlas and the persisted world still agree — the
     * third acceptance criterion. Nothing is moved, nothing is deleted, and a marker already standing in its
     * chunk is left alone, so running this twice places nothing the second time.
     *
     * <p>Matched on (chunk, kind) rather than on kind alone: two specs of the same kind are placed in different
     * chunks on purpose (two old-growth stands, two springs), and matching by kind would silently drop the
     * second.
     */
    @Transactional
    public EcologySummary reconcile() {
        WorldGenesisService.GenesisSummary world = worldGenesis.current();
        if (world == null) return new EcologySummary(null, 0);

        List<WorldGenesisService.PreviewMarker> markers = worldGenesis.markerPlan(new WorldGenesisService.GenesisRequest(world.seed(), world.widthChunks(), world.heightChunks()));
        int placed = 0;
        for (WorldGenesisService.PreviewMarker marker : markers) {
            UUID chunkId = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id = ? AND grid_x = ? AND grid_y = ?", UUID.class, world.worldId(), marker.x(), marker.y());
            Integer already = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecology_site WHERE world_id = ? AND chunk_id = ? AND site_kind = ?",
                    Integer.class, world.worldId(), chunkId, marker.label());
            if (already != null && already > 0) continue;
            UUID siteId = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'ECOLOGY_SITE', ?, ?)", siteId, marker.label(), chunkId);
            jdbc.update("INSERT INTO ecology_site (id, world_id, chunk_id, site_category, site_kind, baseline_abundance) VALUES (?, ?, ?, ?, ?, ?)",
                    siteId, world.worldId(), chunkId, marker.category(), marker.label(), abundanceFor(marker.category()));
            placed++;
        }
        // The same argument as the markers above, for the plants: a world generated before genesis planted anything
        // would otherwise stay bare for ever, and the only world anybody is playing is one of those. Additive, so a
        // stand already there — including one worked half down — is left alone.
        int stands = seedFlora(world.worldId());
        if (placed > 0 || stands > 0)
            jdbc.update("INSERT INTO world_event (occurred_at, event_type, aggregate_id, payload) VALUES (now(), 'WORLD_ECOLOGY_RECONCILED', ?, jsonb_build_object('sitesAdded', ?, 'standsAdded', ?, 'source', 'approved-overseer-atlas'))", world.worldId(), placed, stands);
        return new EcologySummary(world.worldId(), placed);
    }

    private int abundanceFor(String category) {
        return switch (category) { case "RESOURCE" -> 780; case "WILDLIFE" -> 700; case "MONSTER" -> 540; default -> 1000; };
    }

    public record EcologySummary(UUID worldId, int siteCount) { }
}
