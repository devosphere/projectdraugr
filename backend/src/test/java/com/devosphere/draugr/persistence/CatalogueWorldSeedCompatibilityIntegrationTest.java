package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The activation gate (#161): no catalogue entry may be declared on ground the world does not make, and no ground
 * the world makes may be barren.
 *
 * <p>This is the failure that has already happened three times. {@code RIVER_BANK} was named by thirteen mineral
 * affinities and sixteen code paths while {@code terrainAt} never emitted it (#156). {@code COAST} carried sea
 * beet, samphire, an osprey and a bonecrab with nowhere in the world any of them could be (#157). Cave animals
 * denned on bare mountaintops because there was no cave (#158). Each time the catalogue looked complete and each
 * time a whole family of entries was unreachable, silently, for months.
 *
 * <p>Both directions are checked because both have gone wrong. An entry declared on ground that does not exist can
 * never be found; a biome added without ecology generates a place with nothing in it, which is the mistake a new
 * biome invites. Failures name every offender rather than the first, so the output reads as the report #161 asks
 * for. Skips without Docker.
 */
@SpringBootTest
class CatalogueWorldSeedCompatibilityIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        System.setProperty("java.awt.headless", "true");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorldGenesisService worldGenesis;
    @Autowired WorldEcologyGenesisService ecology;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /**
     * Affinity tokens for every catalogue family, split on the comma rather than matched with LIKE — a token is
     * satisfied only by an exact biome, so a substring accident cannot make an unreachable entry look reachable.
     */
    private static final String REFS =
        "  SELECT 'flora'    AS src, flora_key    AS entry, trim(x) AS token FROM flora_definition,    unnest(string_to_array(biome_affinity, ',')) x " +
        "  UNION ALL SELECT 'mineral',  mineral_key,  trim(x) FROM mineral_definition,  unnest(string_to_array(biome_affinity, ',')) x " +
        "  UNION ALL SELECT 'wildlife', species_key,  trim(x) FROM wildlife_species,    unnest(string_to_array(biome_affinity, ',')) x " +
        "  UNION ALL SELECT 'monster',  species_key,  trim(x) FROM monster_profile,     unnest(string_to_array(biome_affinity, ',')) x " +
        "  UNION ALL SELECT 'insect',   colony_kind,  trim(x) FROM insect_colony_kind,  unnest(string_to_array(biome_affinity, ',')) x ";

    /**
     * No candidate may be stranded: every entry needs at least one piece of ground it can actually live on.
     *
     * <p>This is the failure the ticket calls unsupported activation. An entry whose every affinity names ground
     * the generator does not make can never be found by anybody, however complete the catalogue looks.
     */
    @Test
    void everyCatalogueEntryHasSomewhereItCanActuallyLive() {
        world();

        List<String> stranded = jdbc.queryForList(
            "WITH ground AS (SELECT DISTINCT biome AS token FROM world_chunk), refs AS (" + REFS + ") " +
            "SELECT r.src || ' ' || r.entry || ' can only live on ' || string_agg(r.token, '/') " +
            "  || ', and the world makes none of it' " +
            "FROM refs r WHERE r.token <> '' GROUP BY r.src, r.entry " +
            "HAVING COUNT(*) FILTER (WHERE r.token IN (SELECT token FROM ground)) = 0 ORDER BY 1", String.class);

        assertTrue(stranded.isEmpty(),
            "these catalogue entries are declared only on ground that does not exist, so nothing can ever find "
          + "them — either the generator must make that ground or the entry must be declared where it can "
          + "actually live: " + stranded);
    }

    /**
     * And no affinity may name ground nobody has ever built. A token used by the catalogue but never generated is
     * how {@code RIVER_BANK} and {@code COAST} sat unreachable for months — the entries survived because they
     * named a second biome too, so the stranding check above stayed quiet while a whole habitat did not exist.
     *
     * <p>{@code SALT_DEPOSIT} and {@code CLAY_DEPOSIT} are exempt: they are not biomes at all. {@code gatherMineral}
     * resolves them at runtime from a chunk carrying a salt or clay ecology site, on top of that chunk's real
     * biome, so they are legitimately absent from {@code world_chunk.biome}.
     */
    @Test
    void noAffinityNamesAHabitatTheGeneratorNeverBuilds() {
        world();

        List<String> phantom = jdbc.queryForList(
            "WITH ground AS (SELECT DISTINCT biome AS token FROM world_chunk), refs AS (" + REFS + ") " +
            "SELECT r.token || ' is named by ' || COUNT(*) || ' catalogue entries and generated nowhere' " +
            "FROM refs r WHERE r.token <> '' AND r.token NOT IN ('SALT_DEPOSIT','CLAY_DEPOSIT') " +
            "  AND r.token NOT IN (SELECT token FROM ground) GROUP BY r.token ORDER BY 1", String.class);

        assertTrue(phantom.isEmpty(),
            "these habitats are declared throughout the catalogue and the generator emits none of them, so every "
          + "entry that names one is reachable only by accident of its other affinities: " + phantom);
    }

    /**
     * The other direction: ground the world makes must hold something.
     *
     * <p>OCEAN is exempt deliberately. {@code arrival_viability} rejects it outright — you cannot stand in deep
     * water — and the sea's ecology was put on COAST when the shore was derived (#157), which is where a Chronicle
     * standing at the tide line actually reaches it. Every gather already answers OCEAN with an empty-handed
     * attempt rather than a fault.
     */
    @Test
    void noGroundTheWorldMakesIsBarren() {
        world();

        List<String> barren = jdbc.queryForList(
            "WITH ground AS (SELECT DISTINCT biome FROM world_chunk WHERE biome <> 'OCEAN') " +
            "SELECT g.biome || ' generates but holds ' " +
            "  || (SELECT count(*) FROM mineral_definition m WHERE g.biome = ANY(string_to_array(m.biome_affinity, ','))) || ' minerals, ' " +
            "  || (SELECT count(*) FROM flora_definition f   WHERE g.biome = ANY(string_to_array(f.biome_affinity, ','))) || ' plants, ' " +
            "  || (SELECT count(*) FROM wildlife_species w   WHERE g.biome = ANY(string_to_array(w.biome_affinity, ','))) || ' animals' " +
            "FROM ground g " +
            "WHERE (SELECT count(*) FROM mineral_definition m WHERE g.biome = ANY(string_to_array(m.biome_affinity, ','))) = 0 " +
            "   OR (SELECT count(*) FROM flora_definition f   WHERE g.biome = ANY(string_to_array(f.biome_affinity, ','))) = 0 " +
            "   OR (SELECT count(*) FROM wildlife_species w   WHERE g.biome = ANY(string_to_array(w.biome_affinity, ','))) = 0 " +
            "ORDER BY 1", String.class);

        assertTrue(barren.isEmpty(),
            "a Chronicle can stand on this ground and find nothing to gather, nothing growing, or nothing alive — "
          + "a biome added without ecology is a place with nothing in it: " + barren);
    }

    /** Deterministic generation: the same seed and generator must place the same ground every time (#161). */
    @Test
    void theSameSeedMakesTheSameWorld() {
        world();

        var request = WorldGenesisService.GenesisRequest.mvpDefault();
        var counted = worldGenesis.preview(request).biomeCounts();
        var persisted = jdbc.queryForList("SELECT biome, COUNT(*) AS n FROM world_chunk GROUP BY biome ORDER BY biome");

        for (var row : persisted) {
            String biome = (String) row.get("biome");
            int inWorld = ((Number) row.get("n")).intValue();
            Integer inPreview = counted.get(biome);
            assertTrue(inPreview != null && inPreview == inWorld,
                "the Overseer Atlas and the persisted world must agree on every biome — " + biome
              + " is " + inWorld + " in the world and " + inPreview + " in the preview");
        }
        assertTrue(counted.size() == persisted.size(),
            "the preview invented ground the world does not hold: preview " + counted.keySet()
          + " vs world " + persisted.size() + " biomes");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The Atlas and the persisted world must agree on <b>every site's placement and category</b> (#161), not
     * merely on how many there are.
     *
     * <p>What existed before this was a count: the bootstrap test asserts {@code ecology_site} has as many rows as
     * {@code markerPlan} has markers. That would pass with every site seeded on the wrong chunk, under the wrong
     * name, in the wrong category — the numbers would match and the world would be silently unlike its own map.
     * It is the same shape as the marker fallback that put a stranded label at the centre of the map: nothing
     * goes missing, so nothing is noticed.
     *
     * <p>Checked in both directions, because drift can add as easily as it can move: every planned marker must be
     * on the ground the plan names, and no persisted site may exist that the plan never asked for.
     */
    @Test
    void theAtlasAndTheWorldAgreeOnEverySitesPlacementAndCategory() {
        world();
        var current = worldGenesis.current();
        var plan = worldGenesis.markerPlan(new WorldGenesisService.GenesisRequest(
            current.seed(), current.widthChunks(), current.heightChunks()));

        List<java.util.Map<String,Object>> persisted = jdbc.queryForList(
            "SELECT es.site_kind, es.site_category, c.grid_x, c.grid_y " +
            "FROM ecology_site es JOIN world_chunk c ON c.id = es.chunk_id");

        // Multiset comparison: two markers of the same kind may legitimately share a label, so match on the whole
        // tuple and remove as we go rather than asking "does one like this exist".
        List<String> remaining = new java.util.ArrayList<>();
        for (var row : persisted)
            remaining.add(row.get("site_kind") + "|" + row.get("site_category")
                + "|" + row.get("grid_x") + "," + row.get("grid_y"));

        List<String> missing = new java.util.ArrayList<>();
        for (var marker : plan) {
            String wanted = marker.label() + "|" + marker.category() + "|" + marker.x() + "," + marker.y();
            if (!remaining.remove(wanted)) missing.add(wanted);
        }

        assertTrue(missing.isEmpty(),
            () -> "the Atlas plans sites the world did not seed where it said, with the name and category it said"
                + " — " + missing.size() + " of " + plan.size() + ":\n" + String.join("\n", missing));
        assertTrue(remaining.isEmpty(),
            () -> "the world holds sites the Atlas never planned — seeding has drifted from the plan:\n"
                + String.join("\n", remaining));
    }

    /**
     * The seasonal dimension of the matrix (#161): no season may be one in which the world offers nothing.
     *
     * <p>Foraging filters {@code flora_drop} by the current season, and the clock has four — the data uses three.
     * That is not itself wrong: winter is lean, and it is carried by the eighty drops that name no season at all.
     * But a later seasonal pass that gave every drop a season would take winter away entirely and read as
     * tightening realism, so the thing that keeps winter fed is asserted rather than left to hold by accident.
     */
    @Test
    void noSeasonLeavesTheWorldWithNothingToForage() {
        Integer anySeason = jdbc.queryForObject(
            "SELECT COUNT(*) FROM flora_drop WHERE season IS NULL", Integer.class);
        assertTrue(anySeason != null && anySeason > 0,
            "every flora drop names a season, so winter — which no drop names — offers nothing at all");

        List<String> biomes = jdbc.queryForList(
            "SELECT DISTINCT unnest(string_to_array(f.biome_affinity, ',')) FROM flora_drop d " +
            "JOIN flora_definition f ON f.flora_key = d.flora_key WHERE d.season IS NULL", String.class);
        assertTrue(biomes.size() >= 4,
            () -> "out-of-season food must be findable across the world, not on one kind of ground: " + biomes);

        // And no entry may be gated on a season the clock never reaches.
        List<String> impossible = jdbc.queryForList(
            "SELECT DISTINCT season FROM flora_drop " +
            "WHERE season IS NOT NULL AND season NOT IN ('SPRING','SUMMER','AUTUMN','WINTER')", String.class);
        assertTrue(impossible.isEmpty(),
            () -> "a drop gated on a season the clock never reports can never be taken: " + impossible);
    }
}
