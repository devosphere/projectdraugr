package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #64/#215 — a place named for one thing, and a camp that fouls its own water.
 *
 * <p>Two defects, one slice. {@code district_purpose} was the only table in the schema that no Java file so much
 * as named: V47 wrote down nine purposes so a future chronicle would not have to re-derive the vocabulary, and
 * DESIGNATE re-derived it, writing six tags of its own of which exactly one overlapped. {@code purpose_tag} was
 * written by one statement and read by none, so saying "this is the latrine ground" changed nothing at all.
 *
 * <p>And {@code safeWaterSource} called a river bank clean unconditionally, so a keeper could foul their camp to
 * refuse 100 with their own leavings and their livestock's muck, stand on the bank, and drink water carrying no
 * risk whatsoever. Refuse already drew predators, cost the body condition and docked stored food's shelf life;
 * the one thing it could not reach was the water, which is the first thing a fouled camp ruins.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class FouledGroundFoulsTheWaterIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID onRunningWater() {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID bank = jdbc.query("SELECT id FROM world_chunk WHERE biome='RIVER_BANK' ORDER BY grid_y, grid_x LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        assertNotNull(bank, "the world must carve a river for this to mean anything");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", bank, summary.id());
        // No water carried, so DRINK reads the ground rather than a vessel. Set down on the bank rather than
        // destroyed — a destroyed object with a live owner is exactly what the Auditor exists to catch.
        jdbc.update("UPDATE world_object w SET current_owner_id=NULL, current_location_id=? " +
                    "FROM item_instance i WHERE i.object_id=w.id AND w.current_owner_id=? " +
                    "  AND i.item_key IN ('raw_water','clean_water','filtered_water')", bank, summary.id());
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=0, hours_without_water=8 WHERE chronicle_id=?", summary.id());
        return bank;
    }

    private int illness(UUID chronicle) {
        return jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    private void refuse(UUID chunk, int level) {
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,?,?) " +
                    "ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=EXCLUDED.refuse_level, last_updated_at=EXCLUDED.last_updated_at",
            chunk, level, Timestamp.from(Instant.now()));
    }

    /** Clean running water is clean; the same water in a camp choked with refuse is not, and it recovers. */
    @Test
    void aFouledCampHasNoCleanDraw() {
        world();
        UUID bank = onRunningWater();
        UUID chronicle = jdbc.queryForObject("SELECT id FROM chronicle WHERE life_state='LIVING'", UUID.class);

        refuse(bank, 0);
        int before = illness(chronicle);
        var clean = actions.resolve("drink from the river");
        assertEquals("SUCCEEDED", clean.outcome(), () -> "there is running water here: " + clean.perception());
        assertEquals(before, illness(chronicle),
            () -> "clean running water must carry no risk: " + clean.perception());

        // The camp is choked with refuse — their own leavings and the stock's muck.
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=8 WHERE chronicle_id=?", chronicle);
        refuse(bank, 85);
        int beforeFouled = illness(chronicle);
        var fouled = actions.resolve("drink from the river");
        assertEquals("SUCCEEDED", fouled.outcome(), () -> "the water is still there: " + fouled.perception());
        assertTrue(illness(chronicle) > beforeFouled,
            () -> "water drawn in a camp choked with refuse must carry risk: " + fouled.perception());

        // And it comes back. Nothing here is a one-way punishment — MAINTAIN_CAMP clears refuse.
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=8 WHERE chronicle_id=?", chronicle);
        refuse(bank, 0);
        int beforeClean = illness(chronicle);
        var recovered = actions.resolve("drink from the river");
        assertEquals(beforeClean, illness(chronicle),
            () -> "a cleaned camp gets its clean water back: " + recovered.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The designation is the point: the Chronicle said what this ground is for, and a latrine upstream of the pot
     * is not made safe by the stream moving.
     */
    @Test
    void groundGivenOverToWasteIsNeverACleanDraw() {
        world();
        UUID bank = onRunningWater();
        UUID chronicle = jdbc.queryForObject("SELECT id FROM chronicle WHERE life_state='LIVING'", UUID.class);
        refuse(bank, 0);

        var named = actions.resolve("call this place the latrine ground");
        assertEquals("SUCCEEDED", named.outcome(), () -> "naming a place must work: " + named.perception());
        assertEquals("SANITATION", jdbc.queryForObject(
            "SELECT purpose_tag FROM chronicle_named_location WHERE chunk_id=? AND purpose_tag IS NOT NULL LIMIT 1", String.class, bank),
            "the purpose must come from the catalogue, not from a chain of literals");

        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=8 WHERE chronicle_id=?", chronicle);
        int before = illness(chronicle);
        var drunk = actions.resolve("drink from the river");
        assertTrue(illness(chronicle) > before,
            () -> "ground the Chronicle designated for waste is not a clean draw at any refuse level: " + drunk.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * DESIGNATE must not have lost a word on the way into the data. Every meaning the old literal chain could
     * express still resolves, and to the catalogue's tag rather than an invented one.
     */
    @Test
    void everyMeaningTheOldLiteralChainCouldExpressStillResolves() {
        world();
        UUID ground = onRunningWater();

        record Case(String said, String tag) { }
        for (Case c : List.of(
                new Case("call this place the latrine ground", "SANITATION"),
                new Case("name this spot the drinking water draw", "DRINKING"),
                new Case("designate this the sleeping ground", "RESIDENTIAL"),
                new Case("call this ground the storehouse", "WAREHOUSE"),
                new Case("name this place the workshop", "WORKSHOP"),
                new Case("designate this the archive", "LIBRARY"))) {
            var r = actions.resolve(c.said());
            assertEquals("SUCCEEDED", r.outcome(), () -> c.said() + " => " + r.perception());
            assertTrue(jdbc.queryForList(
                    "SELECT purpose_tag FROM chronicle_named_location WHERE chunk_id=? AND purpose_tag IS NOT NULL", String.class, ground)
                    .contains(c.tag()),
                () -> "\"" + c.said() + "\" must still mean " + c.tag());
        }

        // A place named for nothing in particular takes no purpose — the vocabulary does not guess.
        var plain = actions.resolve("call this hollow Fern Hollow");
        assertEquals("SUCCEEDED", plain.outcome(), () -> plain.perception());
        assertEquals(null, jdbc.queryForObject(
            "SELECT purpose_tag FROM chronicle_named_location WHERE chunk_id=? AND name='Fern Hollow'", String.class, ground),
            "an ordinary name is not a purpose");
    }

    /** A seventh vocabulary cannot quietly appear beside the catalogue again. */
    @Test
    void aPurposeThatIsNotInTheCatalogueCannotBeStored() {
        world();
        UUID ground = onRunningWater();
        UUID chronicle = jdbc.queryForObject("SELECT id FROM chronicle WHERE life_state='LIVING'", UUID.class);
        assertThrows(Exception.class, () -> jdbc.update(
            "INSERT INTO chronicle_named_location (chronicle_id,chunk_id,name,purpose_tag,designated_at) VALUES (?,?,?,?,?)",
            chronicle, ground, "Invented District", "SLEEPING", Timestamp.from(Instant.now())),
            "SLEEPING was one of the six the code invented; the catalogue must be the only vocabulary");
    }
}
