package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A coat worth keeping (#106).
 *
 * <p>A kept animal's coat wears, faster where it stands in its own filth; matted, it makes the animal ill and its
 * fleece is not worth the shearing; and a comb in a keeper's hands puts most of it back. That chain is what the
 * grooming tools were named for and what the ticket recorded as missing. Skips without Docker.
 */
@SpringBootTest
class ACoatWorthKeepingIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID tame(UUID chronicle, UUID chunk, String species) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',1,5,'FORAGING',?)", pop, site, species, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
                "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',95,12,?,0,0,0,0)", bond, chronicle, pop, ts);
        return bond;
    }

    private int coat(UUID bond) {
        return jdbc.queryForObject("SELECT coat_condition FROM wildlife_bond WHERE id=?", Integer.class, bond);
    }

    private int sickness(UUID bond) {
        return jdbc.queryForObject("SELECT sickness FROM wildlife_bond WHERE id=?", Integer.class, bond);
    }

    @Test
    void aCoatMatsIsCombedOutAndDecidesWhetherAFleeceIsWorthTaking() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2031-06-10T12:00:00Z");
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(now));
        // The body keeps pace with the clock it is moved to, or the Chronicle starves between one line and the next.
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", Timestamp.from(now));

        UUID sheep = tame(chronicle, chunk, "bighorn_sheep");
        assertEquals(100, coat(sheep), "a newly kept animal is in good coat");

        // A day on clean ground takes a little off it.
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        items.advanceHerdSickness(now);
        assertEquals(100 - PhysicalItemService.COAT_WEARS, coat(sheep), "a coat nobody touches wears");

        // A day standing in filth takes far more, and the animal starts to sicken.
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,90,?) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=EXCLUDED.refuse_level", chunk, Timestamp.from(now));
        int beforeFilth = coat(sheep);
        items.advanceHerdSickness(now);
        assertEquals(beforeFilth - PhysicalItemService.COAT_WEARS_IN_FILTH, coat(sheep), "filth mats a coat far faster than time does");
        assertTrue(sickness(sheep) > 0, "and what is in the coat gets into the animal");

        // Matted: the fleece is not worth taking, and the refusal says why.
        jdbc.update("UPDATE wildlife_bond SET coat_condition=10, sickness=0 WHERE id=?", sheep);
        var sheared = actions.resolve("shear the bighorn sheep");
        assertEquals("TAKE_ANIMAL_YIELD", sheared.intent(), sheared::perception);
        assertEquals("FAILED", sheared.outcome(), sheared::perception);
        assertTrue(sheared.perception().contains("matted"), sheared::perception);

        // Bare hands will not do it. (The world's own turn runs while this test does, and wears the coat as it goes,
        // so what is asserted is that grooming did not RAISE it — never an exact number the tick also moves.)
        int matted = coat(sheep);
        var byHand = actions.resolve("groom the bighorn sheep");
        assertEquals("GROOM_ANIMAL", byHand.intent(), byHand::perception);
        assertEquals("FAILED", byHand.outcome(), byHand::perception);
        assertTrue(coat(sheep) <= matted, "nothing was put back by wishing");

        // Clean ground from here on: the world's own turn adds to a beast standing in filth, and what is being
        // measured below is what the comb did, not what the ground did.
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        // With a comb it comes out, and the animal is better for it.
        items.createCarriedItem(chronicle, "bone_comb", "Bone comb", now, "TEST_FIXTURE");
        jdbc.update("UPDATE wildlife_bond SET sickness=20 WHERE id=?", sheep);
        int before = coat(sheep);
        var combed = actions.resolve("comb out the bighorn sheep coat");
        assertEquals("GROOM_ANIMAL", combed.intent(), combed::perception);
        assertEquals("SUCCEEDED", combed.outcome(), combed::perception);
        assertTrue(coat(sheep) >= before + PhysicalItemService.GROOMING_PUTS_BACK - PhysicalItemService.COAT_WEARS_IN_FILTH,
            () -> "the comb puts most of a coat back: " + before + " -> " + coat(sheep));
        assertTrue(sickness(sheep) < 20, "vermin out of the coat is illness out of the animal");
        assertTrue(jdbc.queryForObject("SELECT use_count FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE w.current_owner_id=? AND i.item_key='bone_comb'", Integer.class, chronicle) > 0, "the comb does the work and wears for it");

        // A clean coat wants nothing.
        jdbc.update("UPDATE wildlife_bond SET coat_condition=100 WHERE id=?", sheep);
        assertEquals("PARTIAL", actions.resolve("groom the bighorn sheep").outcome(), "a clean coat is not worth a keeper's half-hour");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
