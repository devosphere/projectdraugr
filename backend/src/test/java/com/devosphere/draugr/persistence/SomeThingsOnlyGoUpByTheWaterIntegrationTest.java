package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Some things only go up by the water (#77, V431).
 *
 * <p>#77's acceptance criteria open with "Each building defines an <b>eligible biome/site</b>, footprint,
 * ordered stages, input bills…". Nothing gated a structure on its setting: a water draw platform could be
 * raised on open grassland, miles from anything to draw from.
 *
 * <p><b>The code had already written down what to do.</b> Beside the catalogue's one place-conditioned
 * assembly, a well, the comment read: <i>"this is a switch on the construction_kind rather than a new column:
 * the second such assembly is the one that should promote it to data."</i> There are eight, so V431 is that
 * promotion — {@code assembly_definition.site_biomes}, read by {@code advance()}.
 *
 * <p>The eight were chosen <b>from the catalogue's own words</b>, every one of which says in its own narration
 * that it stands at the water: "the way down to the water", "a short staging out over the water", "a flat
 * stone set at the water's edge", "a clay-lined hollow beside the water", "out over the water on its driven
 * piles", "set across the shallows", "a ramp down to the water", "carry a body dry over".
 *
 * <p>And what that phrasing <em>correctly leaves out</em>, which is what shows the method discriminates rather
 * than sweeping: a {@code waterfowl_pool} <b>puddles its own water</b> — "puddle clay into its sides until it
 * holds" — so it does not need a shore to stand beside; it becomes the water. An {@code earth_sheltered_hut}
 * is "cut back into the bank", but that bank is an earth slope, not a river's — the word is shared and the
 * meaning is not.
 *
 * <p>The well stays in code, and not as a dodge: its condition is the water <em>table</em>, which no list of
 * biomes can express. A high water table under grassland takes a well and a dry terrace by a river does not,
 * so a biome list would be wrong in both directions.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class SomeThingsOnlyGoUpByTheWaterIntegrationTest {

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
    @Autowired ChronicleService chronicles;
    @Autowired ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired JdbcTemplate jdbc;

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var active = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(active, "a living Chronicle is needed");
        return active.id();
    }

    private void standOn(UUID chronicle, String biome) {
        UUID chunk = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome=?"
          + " AND NOT EXISTS (SELECT 1 FROM world_object w JOIN construction_project cp ON cp.object_id=w.id"
          + "                  WHERE w.current_location_id=c.id AND w.lifecycle_state='ACTIVE')"
          + " ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class, biome);
        assertNotNull(chunk, () -> "the world must have an unbuilt " + biome + " chunk to stand on");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
    }

    private void give(UUID chronicle, String key, String name, int n) {
        for (int i = 0; i < n; i++) items.createCarriedItem(chronicle, key, name, Instant.now(), "TEST_SEED");
    }

    @Test
    void aWashingPlaceInlandIsRefusedAndSaysWhatGroundItWants() {
        UUID chronicle = chronicle();
        standOn(chronicle, "GRASSLAND");
        give(chronicle, "river_gravel", "River gravel", 4);
        give(chronicle, "field_stone", "Field stone", 4);

        var r = actions.resolve("build a washing place", UUID.randomUUID());
        assertNotNull(r);
        assertEquals("FAILED", r.outcome(),
            () -> "a washing place wants the water's edge, not open grass: " + r.perception());
        assertTrue(r.perception().contains("wants water at hand"),
            () -> "and the refusal must say so: " + r.perception());
        assertTrue(r.perception().contains("grassland"),
            () -> "naming the ground it is standing on, so the player knows to move: " + r.perception());
        assertTrue(r.perception().contains("river bank"),
            () -> "and the ground it wants, spelled in plain words: " + r.perception());
    }

    @Test
    void theSameWorkByTheWaterIsAllowed() {
        // THE POSITIVE CASE OF THE REFUSAL, which this project's own rule insists on: a gate that only ever
        // refuses is indistinguishable from a broken assembly.
        UUID chronicle = chronicle();
        standOn(chronicle, "RIVER_BANK");
        give(chronicle, "river_gravel", "River gravel", 4);
        give(chronicle, "field_stone", "Field stone", 4);

        var r = actions.resolve("build a washing place", UUID.randomUUID());
        assertNotNull(r);
        assertFalse(r.perception().contains("wants water at hand"),
            () -> "on a river bank the ground is right: " + r.perception());
        assertEquals("SUCCEEDED", r.outcome(),
            () -> "and with gravel and stone to hand the work goes ahead: " + r.perception());
    }

    @Test
    void aStructureWithNoSiteRequirementGoesUpAnywhere() {
        // The control. Most of the catalogue is unconstrained and must stay that way — the site gate is for the
        // eight that say they stand at the water, not for everything.
        UUID chronicle = chronicle();
        standOn(chronicle, "GRASSLAND");
        give(chronicle, "dry_branch", "Dry branch", 4);
        give(chronicle, "stone_axe", "Stone axe", 1);

        var r = actions.resolve("build a woodworking stump", UUID.randomUUID());
        assertNotNull(r);
        assertFalse(r.perception().contains("wants water at hand"),
            () -> "a stump needs no shore: " + r.perception());
        assertEquals("SUCCEEDED", r.outcome(), () -> "and goes up on open ground: " + r.perception());
    }

    @Test
    void everyConstrainedStructureHasSomewhereInThisWorldToStand() {
        // The question the MIGRATION could not ask. A migration runs before any world is generated, so its own
        // guard can only check that each biome is a real one (against terrain_going). Whether the generator
        // actually produces enough of that ground is a question for a world, and this is the world.
        //
        // A structure constrained to ground that never generates is unbuildable — the catalogue token this
        // project exists to avoid — so this is the assertion that makes the requirement honest.
        chronicle();
        List<String> stranded = jdbc.queryForList(
            "SELECT d.assembly_key FROM assembly_definition d WHERE d.site_biomes IS NOT NULL"
          + " AND NOT EXISTS (SELECT 1 FROM world_chunk wc"
          + "                  WHERE wc.biome = ANY (string_to_array(d.site_biomes, ','))) ORDER BY 1",
            String.class);
        assertTrue(stranded.isEmpty(),
            () -> "these have nowhere in this world they could ever be built: " + stranded);
    }

    @Test
    void theEightAreTheEightAndThePoolThatMakesItsOwnWaterIsNotAmongThem() {
        chronicle();
        List<String> constrained = jdbc.queryForList(
            "SELECT assembly_key FROM assembly_definition WHERE site_biomes IS NOT NULL ORDER BY 1", String.class);
        assertEquals(List.of("duck_house", "fishing_landing", "fishing_weir", "footbridge", "riverbank_steps",
                             "settling_basin", "washing_place", "water_draw_platform"), constrained,
            "the site requirement belongs to the eight that say they stand at the water, and to nothing else");

        // Asserted by name, because this is the judgement the next cycle is most likely to get wrong.
        assertTrue(jdbc.queryForObject(
            "SELECT site_biomes IS NULL FROM assembly_definition WHERE assembly_key='waterfowl_pool'", Boolean.class),
            "a pool that puddles its own water does not need a shore to stand beside");
        assertTrue(jdbc.queryForObject(
            "SELECT site_biomes IS NULL FROM assembly_definition WHERE assembly_key='earth_sheltered_hut'", Boolean.class),
            "the bank a dugout is cut into is an earth slope, not a river's");
    }

    @Test
    void everyBiomeNamedIsOneTheCatalogueKnows() {
        // terrain_going carries one row per biome and is the only authority a migration can consult. If a
        // typo ever reaches site_biomes, nothing at runtime complains — the structure simply becomes
        // unbuildable everywhere — so it is held here as well as in V431.
        chronicle();
        List<String> unknown = jdbc.queryForList(
            "SELECT DISTINCT trim(b) FROM assembly_definition d, unnest(string_to_array(d.site_biomes, ',')) b"
          + " WHERE d.site_biomes IS NOT NULL"
          + "   AND NOT EXISTS (SELECT 1 FROM terrain_going tg WHERE tg.biome = trim(b)) ORDER BY 1", String.class);
        assertTrue(unknown.isEmpty(), () -> "not a biome the catalogue knows: " + unknown);
    }
}
