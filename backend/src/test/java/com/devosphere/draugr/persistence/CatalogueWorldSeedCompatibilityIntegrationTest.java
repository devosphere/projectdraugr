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
}
