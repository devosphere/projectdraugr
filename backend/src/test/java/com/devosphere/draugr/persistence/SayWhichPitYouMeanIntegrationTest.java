package com.devosphere.draugr.persistence;

import com.devosphere.draugr.assembly.AssemblyService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pit, frame and rack are family words (#77, V427): seven pits, six frames, four racks. The plain phrase must
 * name the family and ask which — and must let what is in hand settle it, so a Chronicle carrying a hay rack's
 * makings who says {@code build a rack} gets a hay rack rather than a question.
 *
 * <p>The question half is only true on ground that holds nothing, because "in reach" counts what lies on the
 * ground as well as what is carried, and the suite shares one world. So the Chronicle is moved to a chunk no
 * object stands on before anything is measured: this test owns its reach. Skips without Docker.
 */
@SpringBootTest
class SayWhichPitYouMeanIntegrationTest {

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
    @Autowired AssemblyService assembly;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private static final List<String> PITS = List.of("compost bay", "cool food pit", "earth oven", "lime burning pit",
        "manure pit", "offal pit", "stone fire pit");
    private static final List<String> FRAMES = List.of("drying rack", "hide frame", "hide scraping frame",
        "milking stanchion", "roofing frame", "smoke rack");
    private static final List<String> RACKS = List.of("drying rack", "harness rack", "hay rack", "smoke rack");

    /** A living Chronicle standing on ground that holds nothing, carrying nothing. */
    private UUID aChronicleOnEmptyGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID empty = jdbc.queryForObject(
            "SELECT wc.id FROM world_chunk wc WHERE NOT EXISTS (SELECT 1 FROM world_object w WHERE w.current_location_id = wc.id) "
          + "ORDER BY wc.id DESC LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", empty, chronicle);
        // It arrives clothed, and that is all; none of what it wears is the makings of a pit, frame or rack.
        List<String> makings = jdbc.queryForList(
            "SELECT DISTINCT i.item_key FROM world_object w JOIN item_instance i ON i.object_id = w.id "
          + "WHERE w.current_owner_id = ? AND i.item_key IN (SELECT r.item_key FROM assembly_stage_requirement r "
          + "JOIN assembly_stage s ON s.stage_key = r.stage_key WHERE s.stage_order = 1)", String.class, chronicle);
        assertTrue(makings.isEmpty(), () -> "a newly awakened Chronicle already carries the makings of something: " + makings);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        return chronicle;
    }

    private UUID here(UUID chronicle) {
        return jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
    }

    private int begun(UUID chronicle) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM assembly_instance WHERE chronicle_id=?", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private void give(UUID chronicle, String itemKey, String name, int n, Instant now) {
        for (int i = 0; i < n; i++) items.createCarriedItem(chronicle, itemKey, name, now, "TEST_SEED");
    }

    @Test
    void emptyHandedTheFamilyIsNamedAndNothingIsBegun() {
        UUID chronicle = aChronicleOnEmptyGround();
        Instant now = ticks.current().simulatedAt();
        for (var family : List.of(new Object[]{"dig a pit", PITS}, new Object[]{"build a frame", FRAMES},
                                  new Object[]{"build a rack", RACKS})) {
            String phrase = (String) family[0];
            @SuppressWarnings("unchecked") List<String> members = (List<String>) family[1];
            String[] r = assembly.advance(chronicle, here(chronicle), phrase, now);
            assertNotNull(r, phrase + " must reach the assembly matcher");
            assertEquals("FAILED", r[0], () -> phrase + " must ask, not build: " + r[1]);
            assertTrue(r[1].contains("Say which you mean"), () -> phrase + " must ask which: " + r[1]);
            for (String m : members)
                assertTrue(r[1].contains(m), () -> phrase + " must name the " + m + " among its family: " + r[1]);
        }
        // The earth oven's first stage asks for nothing, so it is "within reach" in empty hands. It must not
        // settle the question on that alone — that would be a pick presented as a decision.
        assertEquals(0, begun(chronicle), "a question must begin nothing — not even the earth oven, whose first stage needs no makings");
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void whatIsInHandSettlesIt() {
        UUID chronicle = aChronicleOnEmptyGround();
        Instant now = ticks.current().simulatedAt();

        // A hay rack's makings and no other rack's: three branches and two cords. The drying and smoke racks want
        // four branches and the harness rack wants timber, so the question has one answer and it is not asked.
        give(chronicle, "dry_branch", "Dry branch", 3, now);
        give(chronicle, "fiber_cordage", "Fibre cordage", 2, now);
        String[] rack = assembly.advance(chronicle, here(chronicle), "build a rack", now);
        assertNotNull(rack);
        assertEquals("SUCCEEDED", rack[0], () -> "the makings in hand point at the hay rack: " + rack[1]);
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id "
          + "WHERE w.current_location_id=? AND cp.project_kind='HAY_RACK' AND cp.state='COMPLETED'",
            Integer.class, here(chronicle)), "a hay rack must stand where it was asked for");

        // Four branches are the makings of three frames: the question narrows to those, and still asks.
        give(chronicle, "dry_branch", "Dry branch", 4, now);
        String[] frame = assembly.advance(chronicle, here(chronicle), "build a frame", now);
        assertNotNull(frame);
        assertEquals("FAILED", frame[0], () -> "three frames are in reach, so it must still ask: " + frame[1]);
        for (String m : List.of("drying rack", "roofing frame", "smoke rack"))
            assertTrue(frame[1].contains(m), () -> "the " + m + " is in reach and must be offered: " + frame[1]);
        for (String m : List.of("hide frame", "milking stanchion"))
            assertFalse(frame[1].contains(m), () -> "the " + m + " is not in reach and must not be offered: " + frame[1]);

        // Naming the kind settles it outright, whatever else the words share.
        String[] pitHouse = assembly.advance(chronicle, here(chronicle), "build a pit house", now);
        assertNotNull(pitHouse);
        assertFalse(pitHouse[1].contains("Say which you mean"), () -> "a pit house is one thing, not a pit: " + pitHouse[1]);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
