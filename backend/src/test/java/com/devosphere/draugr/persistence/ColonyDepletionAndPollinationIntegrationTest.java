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
 * A colony is a place you can work out, and the small life on your ground fills out what grows on it (#74/#162).
 *
 * <p>Two declared-and-ignored mechanics. {@code insect_colony} carried health, product_ready_at and
 * last_disturbed_at, and {@code insect_colony_kind} carried regrowth_days, for a depletion model that never ran:
 * nothing anywhere created an insect_colony row, so the UPDATE recording disturbance matched nothing and one
 * patch of ground yielded grubs, honey and silk forever. And {@code pollination_bonus} — 30 for a hive, 10 for a
 * worm patch — was read by nothing at all, so keeping bees beside a plot did exactly as much for the harvest as
 * keeping none. Skips without Docker.
 */
@SpringBootTest
class ColonyDepletionAndPollinationIntegrationTest {

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

    private UUID awakenOn(String biome) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome=? ORDER BY grid_y, grid_x LIMIT 1", UUID.class, biome);
        assertNotNull(chunk, "the approved world must contain a " + biome + " chunk");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", summary.id());
        return summary.id();
    }

    @Test
    void groundWorkedForInsectsMustBeLeftToComeBack() {
        UUID chronicle = awakenOn("GRASSLAND");
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        // Summer, when a grassland actually carries the colonies that are seasonal.
        Instant summer = Instant.parse("2026-07-15T10:00:00Z");

        PhysicalItemService.InsectHarvest first = items.collectInsects(chronicle, chunk, "dig earthworms out of the ground", summer);
        assertEquals("SUCCEEDED", first.outcome(), "open ground in summer must yield insects at all: " + first.narration());

        // The working is now recorded on the ground itself — the row that nothing used to create.
        Integer placed = jdbc.queryForObject("SELECT COUNT(*) FROM insect_colony WHERE chunk_id=?", Integer.class, chunk);
        assertNotNull(placed);
        assertTrue(placed > 0, "working a colony must leave a record of it on this ground, or it can never be worked out");

        // Work every colony this ground carries flat, then find there is nothing left to take.
        String lastNarration = first.narration();
        boolean refused = false;
        for (int attempt = 0; attempt < 8 && !refused; attempt++) {
            PhysicalItemService.InsectHarvest again = items.collectInsects(chronicle, chunk, "turn over the ground for insects", summer);
            lastNarration = again.narration();
            if ("FAILED".equals(again.outcome())) refused = true;
        }
        assertTrue(refused, "ground worked over and over must eventually give nothing — it gave forever before: " + lastNarration);
        assertTrue(lastNarration.toLowerCase(java.util.Locale.ROOT).contains("come back")
                || lastNarration.toLowerCase(java.util.Locale.ROOT).contains("ready"),
            "the refusal must say the ground needs time, not that there was never anything here: " + lastNarration);

        // Left alone past its regrowth, the same ground gives again.
        Instant nextSeason = summer.plus(Duration.ofDays(40));
        assertEquals("SUCCEEDED", items.collectInsects(chronicle, chunk, "dig earthworms out of the ground", nextSeason).outcome(),
            "ground left alone long enough must come back");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void beesOnTheGroundFillOutTheHarvest() {
        UUID chronicle = awakenOn("GRASSLAND");
        Instant summer = Instant.parse("2026-07-20T10:00:00Z");

        // Two fresh fields of the same ground, so nothing but the bees differs between them — reaping twice on
        // one chunk would draw its fertility down and confound the comparison.
        java.util.List<UUID> fields = jdbc.queryForList(
            "SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x LIMIT 2", UUID.class);
        assertEquals(2, fields.size(), "this test needs two stretches of open ground");
        UUID tended = fields.get(0), barren = fields.get(1);

        // Break every pollinator on the second field down to nothing. A colony robbed flat stops working the
        // ground, and the field feels it.
        for (String kind : jdbc.queryForList(
                "SELECT colony_kind FROM insect_colony_kind WHERE pollination_bonus > 0", String.class))
            wreck(barren, kind, summer);

        int withBees = reapOneStand(chronicle, tended, summer);
        int withoutBees = reapOneStand(chronicle, barren, summer);

        assertTrue(withBees > withoutBees,
            "a hive working this ground must fill the stand out — reaped " + withBees + " with bees and " + withoutBees + " without");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Put a colony of this kind on the ground with nothing left in it. */
    private void wreck(UUID chunk, String colonyKind, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE',?,?)",
            id, "Spent " + colonyKind.replace('_', ' '), chunk);
        jdbc.update("INSERT INTO insect_colony (object_id,colony_kind,chunk_id,health,last_disturbed_at) VALUES (?,?,?,0,?)",
            id, colonyKind, chunk, Timestamp.from(at));
    }

    /** Sow a ripe stand directly and reap it, returning how many heads came in. */
    private int reapOneStand(UUID chronicle, UUID chunk, Instant at) {
        jdbc.update("DELETE FROM crop_stand WHERE chunk_id=?", chunk);
        jdbc.update("INSERT INTO crop_stand (id,chunk_id,crop_key,sown_at,maturity_days,tilled,grazed,harvested) VALUES (?,?,'wild_grain',?,?,true,false,false)",
            UUID.randomUUID(), chunk, Timestamp.from(at.minus(Duration.ofDays(30))), 20);
        Integer before = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=? AND i.item_key='wild_grain_head'",
            Integer.class, chronicle);
        String[] r = items.harvestCrop(chronicle, chunk, at);
        assertEquals("SUCCEEDED", r[0], "the seeded stand must be ripe and reapable: " + r[1]);
        Integer after = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=? AND i.item_key='wild_grain_head'",
            Integer.class, chronicle);
        assertNotNull(before); assertNotNull(after);
        return after - before;
    }
}
