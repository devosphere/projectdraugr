package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.simulation.WeatherSimulationService;
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
 * Cross-sector consequence-integrity long-play (EPIC #215, story #221 — first scenario pack). The consequence
 * systems built across #215 — labour physiology (#217: overexertion strain, sweat, dehydration), tool wear
 * (#220), camp sanitation (#218: refuse → illness/pests, latrine + tidy counter-plays), and structure weathering
 * (#220) — were each proven in isolation. This exercises them TOGETHER over one lived-in play and asserts they
 * coexist without breaking the shared invariants: a clean Persistent State Auditor at every step, immutable
 * history for what happened, current-state authority, and no silent despawn.
 *
 * <p>One camp, one season: a Chronicle raises a fenced, latrined camp; fells while spent (labour strains the body
 * and wears the axe); lives there (eating fouls the ground); tidies it (the refuse is carried off); and the season
 * turns (the field structures weather). The world stays sound throughout. Skips gracefully without Docker.
 */
@SpringBootTest
class ConsequenceIntegrityLongPlayIntegrationTest {

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
    @Autowired com.devosphere.draugr.action.ChronicleActionService actions;
    @Autowired ConstructionService construction;
    @Autowired WeatherSimulationService weather;
    @Autowired WildlifeSimulationService sim;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void sound(String where) {
        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent " + where + ": " + auditor.inspect().violations());
    }

    private int refuse(UUID chunk) {
        Integer v = jdbc.query("SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?", rs -> rs.next() ? rs.getInt(1) : null, chunk);
        return v == null ? 0 : v;
    }

    private void fabricate(UUID chunk, String kind, String name, Timestamp at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)", id, name, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) VALUES (?,?,'COMPLETED',100,?,100,?)",
                id, kind, at, at);
    }

    @Test
    void theConsequenceSystemsCoexistAndStaySoundOverALivedInSeason() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant base = ticks.current().simulatedAt();

        // Phase A — a fenced, latrined camp on this ground, and an axe to work with.
        weather.advanceTo(base); // seed the weather row the weathering tick reads
        fabricate(chunk, "WATTLE_FENCE", "Wattle fence", Timestamp.from(base));
        fabricate(chunk, "LATRINE", "Camp latrine", Timestamp.from(base));
        items.createCarriedItem(chronicle, "stone_axe", "Stone axe", base, "TEST_SEED");
        sound("at camp setup");

        // Phase B — felling while spent: labour strains the body (a STRAIN kept in history) and wears the axe.
        jdbc.update("UPDATE chronicle_physiology SET energy_level=8, injury_severity=0, wetness_level=0, hours_without_food=20, " +
                "hours_without_water=8, illness_severity=0, last_metabolic_update=? WHERE chronicle_id=?", Timestamp.from(ticks.current().simulatedAt()), chronicle);
        com.devosphere.draugr.action.ChronicleActionService.ActionResult felled = actions.resolve("I fell a tree.");
        assertEquals("SUCCEEDED", felled.outcome(), () -> "felling must succeed: " + felled.perception());
        assertTrue(jdbc.queryForObject("SELECT use_count FROM item_instance i JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=? AND i.item_key='stone_axe'", Integer.class, chronicle) >= 1,
                "the axe must wear with the felling (#220)");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_condition_event WHERE chronicle_id=? AND condition_kind='STRAIN'", Integer.class, chronicle) >= 1,
                "heavy labour while spent must leave a STRAIN in history (#217)");
        sound("after spent labour");

        // Phase C — living at the camp fouls the ground: eating leaves refuse.
        for (int i = 0; i < 3; i++) {
            items.createCarriedItem(chronicle, "dried_mushroom", "Dried mushroom", ticks.current().simulatedAt(), "TEST_SEED");
            actions.resolve("I eat the dried mushroom.");
        }
        int fouled = refuse(chunk);
        assertTrue(fouled > 0, "living at the camp must foul the ground with refuse (#218)");
        sound("after living at camp");

        // Phase D — tidying the camp carries off the refuse and tends the structures (kept in history).
        com.devosphere.draugr.action.ChronicleActionService.ActionResult tidied = actions.resolve("I tidy the camp.");
        assertEquals("SUCCEEDED", tidied.outcome(), () -> "tidying the camp must succeed: " + tidied.perception());
        assertTrue(refuse(chunk) < fouled, "tidying must carry off refuse (#218)");
        sound("after tidying");

        // Phase E — the season turns. The field structures weather over three weeks; the world ages without the
        // Chronicle starving (the world systems advance directly, not the metabolism).
        Instant later = base.plus(Duration.ofDays(21));
        construction.advanceTo(later);
        sim.advanceTo(later);
        int fenceIntegrity = jdbc.queryForObject("SELECT integrity_percent FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
                "WHERE w.current_location_id=? AND cp.project_kind='WATTLE_FENCE'", Integer.class, chunk);
        assertTrue(fenceIntegrity < 100 && fenceIntegrity > 0, () -> "field structures must weather over a season but not vanish (was " + fenceIntegrity + ") (#220)");
        sound("after the season turns");

        // Final — the whole run stays sound: the Chronicle was never silently despawned, and the camp's history is kept.
        assertEquals("LIVING", jdbc.queryForObject("SELECT life_state FROM chronicle WHERE id=?", String.class, chronicle),
                "the Chronicle must persist across the play, never silently despawned");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE transition_type='FELLED'", Integer.class) >= 1,
                "what happened must be kept in immutable history (the felling left FELLED transitions)");
        sound("at the end of the season");
    }
}
