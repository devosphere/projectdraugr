package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flax → linen (EPIC #171 / #173 retting & scutching, #174/#175 the plant side of spinning & weaving). The wool road
 * to cloth was whole; the plant road dead-ended at cordage. This proves the new road end to end through the public
 * action pipeline: ret the flax, scutch it to line fibre, spin it to thread, weave it to cloth on the loom, and sew
 * the cloth into a linen shift to wear — each step a real material process consuming its inputs. Skips without Docker.
 */
@SpringBootTest
class LinenTextileIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    private void give(UUID chronicle, String key, int n, Instant at) {
        for (int i = 0; i < n; i++) items.createCarriedItem(chronicle, key, key, at, "TEST_SEED");
    }

    @Test
    void flaxIsRettedScutchedSpunWovenAndSewnIntoALinenShift() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        // Retting and leaching are wet work, so give this ground water on purpose. It used to rely on the
        // arrival chunk happening to be a marsh, which is not something a test should be betting on (#156).
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldHere = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, here);
        UUID springHere = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Freshwater spring',?)", springHere, here);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'RESOURCE','Freshwater spring',700)", springHere, worldHere, here);
        Instant now = ticks.current().simulatedAt();

        // 1. RET — flax stalks retted in standing water free the fibre (#173).
        give(chronicle, "flax_stalk", 4, now);
        ChronicleActionService.ActionResult ret = actions.resolve("ret the flax");
        assertEquals("SUCCEEDED", ret.outcome(), () -> "retting flax must succeed: " + ret.perception());
        assertTrue(owned(chronicle, "retted_flax") >= 2, () -> "retting must yield retted flax, got " + owned(chronicle, "retted_flax"));

        // 2. SCUTCH — the retted straw is broken and dressed to clean line fibre (#173).
        give(chronicle, "retted_flax", 3, now);
        ChronicleActionService.ActionResult scutch = actions.resolve("scutch the flax");
        assertEquals("SUCCEEDED", scutch.outcome(), () -> "scutching flax must succeed: " + scutch.perception());
        assertTrue(owned(chronicle, "line_flax") >= 4, () -> "scutching must yield line flax, got " + owned(chronicle, "line_flax"));

        // 3. SPIN — line flax spun to linen thread (#174).
        give(chronicle, "line_flax", 6, now);
        ChronicleActionService.ActionResult spin = actions.resolve("spin linen thread");
        assertEquals("SUCCEEDED", spin.outcome(), () -> "spinning linen must succeed: " + spin.perception());
        assertTrue(owned(chronicle, "linen_thread") >= 2, () -> "spinning must yield linen thread, got " + owned(chronicle, "linen_thread"));

        // 4. WEAVE — thread woven to linen cloth on the loom (#175 plant side).
        give(chronicle, "linen_thread", 4, now);
        ChronicleActionService.ActionResult weave = actions.resolve("weave the linen cloth");
        assertEquals("SUCCEEDED", weave.outcome(), () -> "weaving linen must succeed: " + weave.perception());
        assertTrue(owned(chronicle, "linen_cloth") >= 1, () -> "weaving must yield linen cloth, got " + owned(chronicle, "linen_cloth"));

        // 5. SEW — cloth sewn into a wearable linen shift (the terminal the woven cloth was for).
        give(chronicle, "linen_cloth", 2, now);
        give(chronicle, "linen_thread", 1, now);
        ChronicleActionService.ActionResult sew = actions.resolve("sew a linen shift");
        assertEquals("SUCCEEDED", sew.outcome(), () -> "sewing a linen shift must succeed (not stolen by CRAFT_GARMENT): " + sew.perception());
        assertTrue(owned(chronicle, "linen_shift") >= 1, () -> "sewing must yield a linen shift, got " + owned(chronicle, "linen_shift"));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
