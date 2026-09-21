package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shelter holds the bodies that fit in it (#108, V369).
 *
 * <p>{@code shelters_stock} was read in five places and meant "animals, any of them", so a hen house standing on
 * the ground was what let a keeper's aurochs settle to breed, and a brooder shelter — a warmed box for day-old
 * chicks — counted as a birthing house for a water buffalo. #108's first requirement is that this infrastructure
 * be species-appropriate, and a flag that fits everything is the menu designation the ticket warns against.
 *
 * <p>The rule is a ceiling: a shelter holds its largest declared body and everything smaller. So the same coop
 * that cannot house an aurochs houses a guinea fowl perfectly well, and that asymmetry is what this asserts —
 * a refusal that refused everything would be a different bug wearing the fix's clothes. Skips without Docker.
 */
@SpringBootTest
class AShelterHoldsWhatFitsIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockBefore;

    @BeforeEach
    void saveClock() { clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class); }

    @AfterEach
    void restoreClock() { if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore); }

    /** A tamed beast of this species bonded to the Chronicle, in condition to breed, on its own population. */
    private UUID tame(UUID chronicle, UUID chunk, String species, Instant at) {
        Timestamp ts = Timestamp.from(at);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        // Room for what is born into it. The young mature back into the parent population, and a guinea fowl
        // clutch is six to twelve — against a capacity of five, `population_count <= carrying_capacity` fails and
        // takes the whole tick with it. Nothing here asserts a population count, so the headroom costs nothing.
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,?,'HERBIVORE','DIURNAL',1,500,'FORAGING',?)", pop, site, species, ts);
        UUID bond = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
            "draft_hunger,draft_thirst,draft_fatigue) VALUES (?,?,?,'TAMED',95,12,?,0,0,0)", bond, chronicle, pop, ts);
        return bond;
    }

    /** A completed, intact structure of this kind standing on this ground. */
    private UUID raise(UUID chunk, String kind, Instant at) {
        UUID id = UUID.randomUUID();
        String name = jdbc.queryForObject("SELECT display_name FROM construction_kind WHERE project_kind=?", String.class, kind);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE',?,?)", id, name, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
            "VALUES (?,?,'COMPLETED',100,?,100)", id, kind, Timestamp.from(at));
        return id;
    }

    private int carrying(String species) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tamed_gestation WHERE species_key=?", Integer.class, species);
        return n == null ? 0 : n;
    }

    @Test
    void aHenHouseIsNotSomewhereToKeepAnAurochs() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant t0 = Instant.parse("2031-06-10T08:00:00Z");
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(t0));

        // This class shares its database with the rest of the suite, so the ground is cleared before anything is
        // measured on it: another test's byre standing here would answer for the coop and prove nothing.
        jdbc.update("DELETE FROM tamed_young");
        jdbc.update("DELETE FROM tamed_gestation");
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        // Ruined rather than deleted: a STRUCTURE world_object with no project behind it is a thing the Auditor
        // would rightly object to, and every shelter query here already requires integrity above nothing.
        jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
            "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);

        // The catalogue's own answer, before any of it is exercised: the tiers say what they mean.
        assertEquals("HUGE", jdbc.queryForObject("SELECT size_tier FROM wildlife_species WHERE species_key='aurochs'", String.class));
        assertEquals("SMALL", jdbc.queryForObject("SELECT size_tier FROM wildlife_species WHERE species_key='guinea_fowl'", String.class));
        assertEquals("SMALL", jdbc.queryForObject("SELECT shelters_up_to_size FROM construction_kind WHERE project_kind='POULTRY_COOP'", String.class));
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE (shelters_stock OR shelters_birth OR isolates_sick) AND shelters_up_to_size IS NULL",
            Integer.class), "every structure that holds a body says which bodies fit in it");

        // Two aurochs and a hen house. This is the defect, in the smallest shape it comes in.
        tame(chronicle, chunk, "aurochs", t0);
        tame(chronicle, chunk, "aurochs", t0);
        raise(chunk, "POULTRY_COOP", t0);
        items.advanceBreeding(t0);
        assertEquals(0, carrying("aurochs"),
            "a hen house on the ground is not somewhere a keeper's aurochs are kept, and they do not settle to breed under it");

        // The same coop, a smaller bird. A ceiling holds what fits, so this must still work — a refusal that
        // refused everything would be a different bug wearing this fix's clothes.
        tame(chronicle, chunk, "guinea_fowl", t0);
        tame(chronicle, chunk, "guinea_fowl", t0);
        items.advanceBreeding(t0);
        assertEquals(2, carrying("guinea_fowl"), "the coop is exactly what guinea fowl are kept in");
        assertEquals(0, carrying("aurochs"), "and it is still not an aurochs house");

        // Build them something they fit in, and they settle. #108 asks for the ox shed by name, and this is what
        // it is for: the only structure in the world that both houses and calves the heaviest stock.
        assertEquals("HUGE", jdbc.queryForObject("SELECT shelters_up_to_size FROM construction_kind WHERE project_kind='OX_SHED'", String.class));
        raise(chunk, "OX_SHED", t0);
        items.advanceBreeding(t0);
        assertEquals(2, carrying("aurochs"), "under a shed built for the body, they breed");

        // And the calf lives, every time. A birthing house sets perinatal loss to zero, and before V369 the only
        // structures that could do that for an aurochs were a brooder box and a foaling stall.
        // Two days on, with the pregnancy's own due date brought forward — not a year-long jump. A long jump
        // matures every young animal in the shared database at once, into whatever carrying capacity the test
        // that made them chose, and the overflow fails the statement for everybody.
        Instant born = t0.plus(java.time.Duration.ofDays(2));
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(born));
        jdbc.update("UPDATE tamed_gestation SET due_at=? WHERE species_key='aurochs'", Timestamp.from(born.minusSeconds(3600)));
        items.advanceBreeding(born);
        assertEquals(2, (int) jdbc.queryForObject("SELECT COUNT(*) FROM tamed_young WHERE species_key='aurochs'", Integer.class),
            "a calving shed loses none of them");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
