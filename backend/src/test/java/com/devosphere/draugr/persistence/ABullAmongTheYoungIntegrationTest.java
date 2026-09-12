package com.devosphere.draugr.persistence;

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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #108 — a bull among the young.
 *
 * <p>V302 gave species a temperament and a DANGEROUS animal now hurts the keeper who works on it unrestrained.
 * It did not hurt anything else: a bull, a boar or a buffalo stood in the same fold as the kids and lambs and was
 * no more trouble to them than a goose.
 *
 * <p>That is the last thing #108's boar pen was waiting on, and why V302 deliberately did not build it —
 * restraining an animal while you work on it and keeping it away from the herd are two different jobs, and the
 * second one needs the herd to be able to come to harm.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class ABullAmongTheYoungIntegrationTest {

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

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

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

    private int young() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tamed_young", Integer.class);
    }

    @Test
    void aDangerousAnimalKillsTheYoungUnlessThereIsAYardToKeepItIn() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant born = Instant.parse("2026-05-01T00:00:00Z");

        try {
            jdbc.update("DELETE FROM tamed_young");
            jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=? " +
                        "  AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.separates_dangerous)", chunk);

            // A goat with kids, and nothing dangerous anywhere near them.
            UUID goat = tame(chronicle, chunk, "mountain_goat");
            for (int i = 0; i < 3; i++)
                jdbc.update("INSERT INTO tamed_young (bond_id,species_key,born_at,matures_at) VALUES (?,'mountain_goat',?,?)",
                    goat, Timestamp.from(born.plus(Duration.ofDays(i))), Timestamp.from(born.plus(Duration.ofDays(300))));
            assertEquals(3, young(), "three kids to start");

            items.dangerousStockAmongTheYoung(born);
            assertEquals(3, young(), "a fold with nothing dangerous in it loses no young");

            // Put an aurochs in with them.
            tame(chronicle, chunk, "aurochs");
            items.dangerousStockAmongTheYoung(born);
            assertEquals(2, young(), "a bull among the young takes one");
            items.dangerousStockAmongTheYoung(born);
            assertEquals(1, young(), "and goes on taking them while nothing keeps it apart");

            // Build the yard, and it stops.
            UUID yard = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE','Bull isolation yard',?)", yard, chunk);
            jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                        "VALUES (?,'BULL_ISOLATION_YARD','COMPLETED',100,?,100)", yard, Timestamp.from(born));
            items.dangerousStockAmongTheYoung(born);
            items.dangerousStockAmongTheYoung(born);
            assertEquals(1, young(), "a yard to keep it in is the whole job of the yard");
        } finally {
            jdbc.update("DELETE FROM tamed_young");
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * Three separate jobs, three separate flags. A keeper who built a sick bay must not silently get a bull yard,
     * or a stanchion, and the catalogue is what keeps them apart.
     */
    @Test
    void separatingIsolatingAndRestrainingAreThreeJobs() {
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE (separates_dangerous AND isolates_sick) " +
            "OR (separates_dangerous AND holds_an_animal_still) OR (isolates_sick AND holds_an_animal_still)", Integer.class),
            "separating a dangerous animal, isolating a sick one and restraining one to work on it are three jobs");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE separates_dangerous", Integer.class),
            "there is one bull yard");
        assertTrue(jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE separates_dangerous AND shelters_stock", Integer.class) > 0,
            "a yard that separates stock must be somewhere stock can be kept");
        // #108 names it for the boar, so the boar's word must reach it.
        assertTrue(jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_definition WHERE assembly_key='bull_isolation_yard' AND keywords LIKE '%boar yard%'", Integer.class) > 0,
            "a keeper asking for a boar yard must get it");
        // And BUILD_PEN must not be able to swallow it — the reason it is a yard and not a pen.
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_definition, unnest(string_to_array(keywords, ',')) k " +
            "WHERE assembly_key='bull_isolation_yard' AND trim(k) ~ '(^|\\s)pens?($|\\s)'", Integer.class),
            "BUILD_PEN matches the whole word 'pen' and would swallow the phrase whole");
    }
}
