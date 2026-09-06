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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Salt out of seawater (#157). The shore had ecology and no industry.
 *
 * <p>Nothing in the world produced salt: {@code rock_salt} was only ever gathered as a mineral, and the only
 * salt-adjacent processes were {@code grind_salt}, which mills salt you already have, and {@code brine_fish},
 * which spends it. Evaporating seawater — the dominant historical source anywhere people lived near a coast, and
 * the obvious thing to do standing on a shore — did not exist.
 *
 * <p>The point of the new gate is the distinction it draws. {@code requires_water} is satisfied by a freshwater
 * spring and deliberately excludes the coast, because retting and tanning would be spoiled by brine. This proves
 * the salt pan works at the sea and refuses inland fresh water, which is the whole reason it needed its own flag.
 * Skips without Docker.
 */
@SpringBootTest
class SaltFromTheSeaIntegrationTest {

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

    /** Stand the Chronicle on this chunk and light a fire there, since boiling brine takes one. */
    private void standAtAFire(UUID chronicle, UUID chunk, Instant now) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("DELETE FROM fire_state fs USING construction_project cp, world_object w " +
            "WHERE fs.construction_id=cp.object_id AND w.id=cp.object_id AND w.current_location_id=?", chunk);
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        UUID pit = UUID.randomUUID();
        Timestamp ts = Timestamp.from(now);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit','ACTIVE',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?,100)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,600,?)", pit, ts);
    }

    private int saltCarried(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='rock_salt' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    /** The gate itself: the shore is salt water, an inland marsh is not, whatever the freshwater gate says. */
    @Test
    void theShoreIsSaltWaterAndAFreshwaterMarshIsNot() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID coast = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='COAST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(coast, "the world must have a shore");
        assertTrue(items.saltWaterToWorkWith(coast), "the shore is salt water");

        // A wetland chunk with no open sea against it and no salt site is fresh water, and must refuse the pan
        // even though the ordinary water gate accepts it — that difference is the reason this flag exists.
        UUID marsh = jdbc.queryForObject(
            "SELECT here.id FROM world_chunk here WHERE here.biome='WETLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM world_chunk sea WHERE sea.world_id=here.world_id AND sea.biome='OCEAN' " +
            "                AND abs(sea.grid_x-here.grid_x)<=1 AND abs(sea.grid_y-here.grid_y)<=1) " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=here.id AND lower(es.site_kind) LIKE '%salt%') " +
            "ORDER BY here.grid_y, here.grid_x LIMIT 1", UUID.class);
        assertNotNull(marsh, "the world must have an inland freshwater marsh to contrast with");
        assertTrue(items.waterToWorkWith(marsh), "a marsh is water for retting and tanning");
        assertTrue(!items.saltWaterToWorkWith(marsh), "but it is fresh water, and no salt comes out of it");
    }

    @Test
    void boilingSeawaterAtTheShoreYieldsSaltAndInlandItDoesNot() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Inland first, at a fire, on ground with no sea against it: the work must refuse, and say why.
        UUID inland = jdbc.queryForObject(
            "SELECT here.id FROM world_chunk here WHERE here.biome='GRASSLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM world_chunk sea WHERE sea.world_id=here.world_id AND sea.biome='OCEAN' " +
            "                AND abs(sea.grid_x-here.grid_x)<=1 AND abs(sea.grid_y-here.grid_y)<=1) " +
            "ORDER BY here.grid_y, here.grid_x LIMIT 1", UUID.class);
        assertNotNull(inland, "the world must have inland grassland");
        standAtAFire(chronicle, inland, now);
        int before = saltCarried(chronicle);

        String[] dry = items.runProcess(chronicle, inland, "boil seawater for salt", now);
        assertEquals("FAILED", dry[0], () -> "there is no sea here to boil: " + dry[1]);
        assertTrue(dry[1].toLowerCase().contains("salt water"), () -> "and the refusal must say what is missing: " + dry[1]);
        assertEquals(before, saltCarried(chronicle), "a refused pan yields nothing");

        // Now the shore, with the same fire and the same words.
        UUID coast = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='COAST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(coast, "the world must have a shore");
        standAtAFire(chronicle, coast, now);

        String[] panned = items.runProcess(chronicle, coast, "boil seawater for salt", now);
        assertEquals("SUCCEEDED", panned[0], () -> "boiling seawater at the shore over a fire must yield salt: " + panned[1]);
        assertTrue(saltCarried(chronicle) > before, "and the salt must actually be in hand afterwards");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
