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
 * A workstation you build must ease the work it is for. The catalogue carries buildable weaving, woodworking and
 * stoneworking tables, but a process asks for the bench by ITEM key, so raising one eased nothing — is_workstation
 * is read in a single place, negatively, to decide what decays. Proves weaving at a built loom table lifts the
 * workmanship, exactly as owning the bench would. Skips without Docker.
 */
@SpringBootTest
class BuiltWorkstationEasesCraftIntegrationTest {

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

    /** How many of this item the Chronicle holds at a given workmanship. Counting by grade rather than reading "the
     *  newest" keeps the assertion independent of row ordering, since both runs can land on the same timestamp. */
    private int countAtGrade(UUID chronicle, String itemKey, String grade) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=? AND i.quality_grade=?",
            Integer.class, chronicle, itemKey, grade);
        return n == null ? 0 : n;
    }

    @Test
    void weavingAtABuiltLoomTableLiftsTheWorkmanship() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        // The finished grade is the WORST of the workmanship and the materials — you cannot weave fine cloth from
        // ordinary cordage, and rightly so. The cordage is therefore FINE, so that the station's lift is the only
        // thing that can decide the outcome rather than being masked by the material cap.
        for (int i = 0; i < 8; i++)
            items.createCarriedItem(chronicle, "fiber_cordage", "Fibre cordage", now, "TEST_FIXTURE", com.devosphere.draugr.quality.QualityGrade.FINE);

        // Woven on the ground, with no loom of any sort. The phrase is fixed, so the base workmanship is fixed too.
        var plain = items.executeProcess(chronicle, chunk, "weave_textile", "weave textile", now);
        assertEquals("SUCCEEDED", plain[0], () -> "weaving must succeed with cordage to hand: " + plain[1]);
        assertTrue(countAtGrade(chronicle, "textile_material", "SOUND") >= 1,
            "woven on the ground, the same plain effort gives sound work");
        assertEquals(0, countAtGrade(chronicle, "textile_material", "FINE"),
            "nothing finer than sound should come off bare ground with no loom of any kind");

        // Raise a weaving table on this ground — the catalogue's own workstation for a loom.
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
            "SELECT is_workstation FROM construction_kind WHERE project_kind='WEAVING_TABLE'", Boolean.class),
            "the weaving table must be declared a workstation for this test to mean anything");
        UUID table = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Weaving table','ACTIVE',?)", table, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'WEAVING_TABLE','COMPLETED',100,?,100)", table, Timestamp.from(now));

        var atTable = items.executeProcess(chronicle, chunk, "weave_textile", "weave textile", now);
        assertEquals("SUCCEEDED", atTable[0], () -> "weaving at the table must succeed: " + atTable[1]);

        assertTrue(countAtGrade(chronicle, "textile_material", "FINE") >= 1,
            "woven at a built loom table, the same phrase and the same materials must come out finer");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
