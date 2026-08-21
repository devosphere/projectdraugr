package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fire-hardened spear regression (M1 #75/#77 primitive tools — closing a dead-read that turned an UPGRADE into a
 * downgrade). Fire-hardening a spear (fire_harden_spear, V77) consumes the primitive_spear and yields a
 * fire_hardened_spear — but combat read only ('stone_axe','primitive_spear','poisoned_spear') as hand weapons, so
 * the hardened spear counted as NO weapon at all: hardening the point literally DISARMED the Chronicle. Now the
 * fire-hardened spear is a hand weapon like any spear (+35) with a small edge on top for its lasting point, so the
 * ordering runs bare &lt; primitive &lt; fire-hardened &lt; poisoned.
 *
 * <p>Proven over a fixed, well-spread battery of encounters against one quarry: a fire-hardened spear lands
 * strictly more kills than bare hands, and at least as many as a plain primitive spear. Skips without Docker.
 */
@SpringBootTest
class FireHardenedSpearIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Count kills across a fixed battery, healing the Chronicle and resetting the quarry before each so every
     *  roll faces the same fresh fight (confront's roll is deterministic in the action id). */
    private int kills(UUID chronicle, UUID chunk, UUID quarry, Instant now, List<UUID> actionIds) {
        int killed = 0;
        for (UUID action : actionIds) {
            jdbc.update("UPDATE chronicle_physiology SET energy_level=90, injury_severity=0, pain_level=0, illness_severity=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE wildlife_population SET population_count=1000, behavior_state='FORAGING' WHERE id=?", quarry);
            if ("SUCCEEDED".equals(wildlife.confront(chronicle, chunk, action, now).outcome())) killed++;
        }
        return killed;
    }

    private void equipInHand(UUID chronicle, String itemKey, Instant now) {
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicle);
        UUID item = items.createCarriedItem(chronicle, itemKey, itemKey, now, "TEST_SEED");
        jdbc.update("INSERT INTO equipment_attachment(item_id, chronicle_id, body_position, layer) VALUES (?,?,'HAND_RIGHT','CARRIED')", item, chronicle);
    }

    @Test
    void fireHardeningASpearArmsTheChronicleRatherThanDisarmingThem() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        // Make our quarry the only creature that can be met here (confront picks the highest-role population with a
        // count > 0 at the chunk); a plain herbivore keeps the resistance low so the weapon is what tips a kill.
        jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Deer range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Deer range',400)", site, worldId, chunk);
        UUID quarry = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'red_deer','HERBIVORE','DIURNAL',1000,2000,'FORAGING',?)", quarry, site, ts);

        // A fixed, well-spread set of encounters (seeded, same every run; confront's roll is floorMod(hash,100)).
        java.util.Random rnd = new java.util.Random(1977);
        List<UUID> actionIds = new ArrayList<>();
        for (int i = 0; i < 120; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        int bareHanded = kills(chronicle, chunk, quarry, now, actionIds);
        equipInHand(chronicle, "primitive_spear", now);
        int primitiveSpear = kills(chronicle, chunk, quarry, now, actionIds);
        equipInHand(chronicle, "fire_hardened_spear", now);
        int fireHardenedSpear = kills(chronicle, chunk, quarry, now, actionIds);

        assertTrue(primitiveSpear > bareHanded,
                () -> "a plain spear must out-kill bare hands (sanity): spear=" + primitiveSpear + " bare=" + bareHanded);
        assertTrue(fireHardenedSpear > bareHanded,
                () -> "a fire-hardened spear must ARM the Chronicle, not disarm them: hardened=" + fireHardenedSpear + " bare=" + bareHanded + " (#75/#77)");
        assertTrue(fireHardenedSpear >= primitiveSpear,
                () -> "a fire-hardened spear must be at least as deadly as a plain one: hardened=" + fireHardenedSpear + " primitive=" + primitiveSpear + " (#75/#77)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
