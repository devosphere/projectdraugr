package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A furnace that holds its heat (#160, V305). Ordinary clay slumps at smelting heat and takes the wall with it;
 * fire clay does not, which is the whole reason a bloomery is lined. A lined shaft loses less of the charge to
 * the slag: the bare one recovers one bloom from two ore, the lined one two blooms from three, on the same three
 * charcoal that the bare shaft would have to burn twice over to match.
 *
 * <p>Two things are proven here, because either one alone would be a catalogue token.
 *
 * <p>FIRST, that fire clay can be asked for. The intent classifier used to name its minerals as literals — flint,
 * chert, obsidian, pyrite — so a mineral added to the catalogue was dug by nobody, whatever the data said. V305
 * moves that list into the table, narrowed to minerals with a {@code tool_required}: those are the ones you break
 * out of rock and must therefore name, and narrowing it is what keeps "gather field stone" with GATHER_STONE and
 * "dig clay" with GATHER_CLAY, both of which are also minerals here. Limestone, which the literal list never
 * carried at all, becomes diggable by name as a side effect.
 *
 * <p>SECOND, that the lining buys something. The lined furnace is raised through the real router — not by process
 * key — because the routing is where this kind of slice usually dies: a CRAFT process cannot answer a text the
 * vocabulary scores as CONSTRUCT, however good its keywords are. Then the same two ore and three charcoal are
 * smelted twice, once in each furnace, and the blooms are counted.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class AFurnaceThatHoldsItsHeatIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Blooms this chronicle is actually holding. Asked of the database rather than tracked, because siblings
     *  share one container and this test must not assume it is the only one that has ever smelted. */
    private int bloomsHeld(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='iron_bloom' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'",
                Integer.class, chronicle);
    }

    @Test
    void aLinedFurnaceDrawsTwiceTheIronFromTheSameOre() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        Timestamp ts = Timestamp.from(now);

        // A smelt burns, so there must be a fire at this place.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,600,?)", pit, ts);

        // The catalogue half: fire clay is a mineral you must break out of the ground, which is what makes the
        // classifier hear it named. Without the tool requirement it would fall back to being surface clay and
        // GATHER_CLAY would take the text.
        assertEquals("STRIKING", jdbc.queryForObject(
                "SELECT tool_required FROM mineral_definition WHERE mineral_key='fire_clay'", String.class),
                "fire clay must be a dug mineral, or nothing will hear it asked for");

        // The classifier half, at the boundary the intent rule actually calls. Asserted rather than inferred,
        // because 'the mineral exists' and 'a player can ask for it' are the two things this project keeps
        // mistaking for each other.
        assertTrue(items.namesADugMineral("dig for fire clay"), "fire clay must be diggable by name");
        assertTrue(items.namesADugMineral("prospect for limestone"),
                "limestone was never in the classifier's hand-written list and should be reachable now too");
        assertFalse(items.namesADugMineral("gather clay"), "plain clay still belongs to GATHER_CLAY");
        assertFalse(items.namesADugMineral("gather field stone"), "field stone still belongs to GATHER_STONE");
        assertFalse(items.namesADugMineral("walk into the forest"), "no mineral is named here at all");

        // Baseline. Raise an ordinary bloomery and smelt: two ore and three charcoal, one bloom.
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "clay_lump", "Clay lump", now, "TEST_SEED");
        for (int i = 0; i < 8; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        String[] plain = items.runProcess(chronicle, chunk, "make a bloomery furnace", now);
        assertEquals("SUCCEEDED", plain[0], () -> "raising an ordinary bloomery must still succeed: " + plain[1]);

        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "iron_ore", "Iron ore", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        int before = bloomsHeld(chronicle);
        String[] bare = items.runProcess(chronicle, chunk, "smelt the iron ore", now);
        assertEquals("SUCCEEDED", bare[0], () -> "the ordinary smelt must be untouched by this slice: " + bare[1]);
        assertEquals(1, bloomsHeld(chronicle) - before, "a bare bloomery returns one bloom, exactly as it always did");

        // Now line one. Raised through the router from the words a player would write, so the CONSTRUCT category,
        // the keyword and the subject term are all proven to agree at runtime rather than on paper.
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "fire_clay", "Fire clay", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "clay_lump", "Clay lump", now, "TEST_SEED");
        for (int i = 0; i < 8; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        String[] lined = items.runProcess(chronicle, chunk, "build a lined bloomery furnace", now);
        assertEquals("SUCCEEDED", lined[0], () -> "a lined bloomery must be raisable from the words for it: " + lined[1]);
        assertTrue(items.hasAtLeast(chronicle, "lined_bloomery_furnace", 1), "raising it must yield the furnace itself");
        assertFalse(items.hasAtLeast(chronicle, "fire_clay", 1), "the lining must be consumed into the wall, not kept");

        // Now the lined shaft. Three ore and the same three charcoal, and it returns two blooms — where the bare
        // shaft would have needed two firings, four ore and six charcoal to reach the same two. The lining buys
        // recovery, not matter: two blooms weigh more than two ore do, and the first draft of this recipe asked
        // for exactly that and was caught by the Auditor.
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "iron_ore", "Iron ore", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        int beforeHot = bloomsHeld(chronicle);
        String[] hot = items.runProcess(chronicle, chunk, "smelt iron in the lined furnace", now);
        assertEquals("SUCCEEDED", hot[0], () -> "the hot smelt must run at the lined furnace: " + hot[1]);
        assertEquals(2, bloomsHeld(chronicle) - beforeHot,
                "the whole point of the lining: two blooms off a charge the bare shaft got one out of");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
