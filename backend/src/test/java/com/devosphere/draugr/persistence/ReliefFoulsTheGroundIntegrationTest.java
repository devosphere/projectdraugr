package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
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
 * The ox's dung fouled the camp and the keeper's did not (#77/#218).
 *
 * <p>{@code chunk_refuse} is a wired consequence — it draws wildlife, costs the body condition, and lets pests dock
 * the shelf life of stored food. Butchering puts refuse on the ground, kept livestock put refuse on the ground, a
 * monster's lair puts refuse on the ground. Relieving yourself put none there at all: it created a WASTE
 * {@code world_object} at the Chronicle's feet, every time, for ever, and nothing in this build has ever read one.
 *
 * <p>That also hid the latrine's own purpose. It halves the passive hygiene loss and drains refuse at four an hour,
 * but nothing a person did without one ever put refuse on the ground to drain.
 *
 * <p>Asserted as an asymmetry in both directions: bare ground takes it, a pit takes it instead, and the WASTE
 * object is still created either way because the act itself is unchanged. Skips without Docker.
 */
@SpringBootTest
class ReliefFoulsTheGroundIntegrationTest {

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
    @Autowired ConstructionService construction;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int refuseAt(UUID chunk) {
        Integer level = jdbc.queryForObject("SELECT COALESCE((SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?),0)", Integer.class, chunk);
        return level == null ? 0 : level;
    }

    private int wasteObjects(UUID chunk) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE object_type='WASTE' AND current_location_id=?", Integer.class, chunk);
        return n == null ? 0 : n;
    }

    @Test
    void reliefFoulsBareGroundAndAPitTakesItInstead() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Instant now = ticks.current().simulatedAt();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        assertNotNull(chunk, "the Chronicle stands somewhere");

        // Clean ground, and no pit anywhere near it.
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        jdbc.update("UPDATE construction_project SET integrity_percent=0 WHERE object_id IN " +
            "(SELECT id FROM world_object WHERE current_location_id=?)", chunk);
        assertEquals(0, refuseAt(chunk), "the ground starts clean");
        int wasteBefore = wasteObjects(chunk);

        // Bare ground. It takes what is left on it, and the amounts differ the way the two acts do.
        ChronicleActionService.ActionResult bowel = actions.resolve("defecate");
        assertEquals("SUCCEEDED", bowel.outcome(), () -> "relieving the bowels must succeed: " + bowel.perception());
        assertEquals(4, refuseAt(chunk), "bowels on bare camp ground leave a day of an ox's fouling behind");

        ChronicleActionService.ActionResult bladder = actions.resolve("urinate");
        assertEquals("SUCCEEDED", bladder.outcome(), () -> "passing water must succeed: " + bladder.perception());
        assertEquals(5, refuseAt(chunk), "passing water leaves a quarter of what the other does");

        // The act itself is unchanged: the waste is still a real object standing on the ground where it was left.
        assertEquals(wasteBefore + 2, wasteObjects(chunk), "both acts still leave a WASTE object with a location");

        // Now dig the pit the world has always had, on this very ground.
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "reed_bundle", "Reed bundle", now, "TEST");
        items.createCarriedItem(chronicle, "digging_stick", "Digging stick", now, "TEST");
        String[] dug = construction.buildLatrine(chronicle, chunk, now);
        assertEquals("SUCCEEDED", dug[0], () -> "the latrine must actually be dug: " + dug[1]);
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
            "JOIN world_object w ON w.id=cp.object_id WHERE w.current_location_id=? AND ck.takes_relief AND cp.state='COMPLETED')",
            Boolean.class, chunk)), "the dug latrine is a kind that takes relief — asked of the data, not of its name");

        int withPit = refuseAt(chunk);
        ChronicleActionService.ActionResult intoThePit = actions.resolve("defecate");
        assertEquals("SUCCEEDED", intoThePit.outcome(), () -> "relieving the bowels at a latrine must succeed: " + intoThePit.perception());
        assertEquals(withPit, refuseAt(chunk), "where a pit stands, the camp ground takes nothing");
        assertTrue(intoThePit.perception().contains("pit"),
            () -> "and the Chronicle is told which of the two things happened: " + intoThePit.perception());

        // The asymmetry stated as one comparison, because a pit that changed nothing would pass every line above.
        jdbc.update("UPDATE construction_project SET integrity_percent=0 WHERE object_id IN " +
            "(SELECT w.id FROM world_object w JOIN construction_project cp ON cp.object_id=w.id " +
            " JOIN construction_kind ck ON ck.project_kind=cp.project_kind WHERE w.current_location_id=? AND ck.takes_relief)", chunk);
        int beforeAgain = refuseAt(chunk);
        actions.resolve("defecate");
        assertTrue(refuseAt(chunk) > beforeAgain,
            "with the pit fallen in the same act fouls the ground again — the pit is what made the difference");

        // A ground that has taken enough of this says so, before it starts drawing animals rather than after.
        jdbc.update("UPDATE chunk_refuse SET refuse_level=30 WHERE chunk_id=?", chunk);
        ChronicleActionService.ActionResult onFoulGround = actions.resolve("defecate");
        assertTrue(onFoulGround.perception().contains("smell of use"),
            () -> "a camp growing foul is witnessed, not left to be inferred from a number: " + onFoulGround.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
