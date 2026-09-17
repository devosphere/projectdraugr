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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hive you keep (#77, V338).
 *
 * <p>Pollination is wired and real: a field on ground the honeybee works gives more. But the bees are where the
 * world put them — forest, highland and open grass — and they do not work the river bank, the marsh margin or the
 * shore. That is exactly where the best fields are, because a floodplain wins its fertility back at 5 a day against
 * a meadow's 2, so the ground a Chronicle most wants to farm had no pollinator on it and nothing they could do.
 *
 * <p>Three fields on the same kind of ground, sown and rested identically: one bare, one with a sound skep beside
 * it, one with a skep burnt out to nothing. The middle field must reap fuller than both.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AHiveYouKeepIntegrationTest {

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

    private int grainHeads(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='wild_grain_head' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'",
            Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aFieldWithABeeSkepBesideItReapsFullerThanOneTheWildBeesNeverReach() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Midsummer, so the season can never be what decides this — bees fly, and the only difference between the
        // fields is whether one is kept beside them.
        Instant summer = Instant.parse("2027-07-15T10:00:00Z");
        List<UUID> fields = jdbc.queryForList(
            "SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 3", UUID.class);
        assertEquals(3, fields.size(), "the fixture needs three pieces of ground");

        UUID bare = fields.get(0), kept = fields.get(1), burnt = fields.get(2);
        List<String> were = jdbc.queryForList(
            "SELECT biome FROM world_chunk WHERE id IN (?,?,?) ORDER BY grid_y, grid_x", String.class, bare, kept, burnt);

        try {
            // River bank: fine farming ground that the wild honeybee has no affinity for. That is the whole gap a
            // kept hive answers, and if the catalogue ever gives the bees this ground the migration's own guard
            // says so before this test ever runs.
            for (UUID field : List.of(bare, kept, burnt)) {
                jdbc.update("UPDATE world_chunk SET biome='RIVER_BANK' WHERE id=?", field);
                ripeField(field, summer);
            }
            skep(kept, summer, 85);
            skep(burnt, summer, 0);

            int withoutBees = reap(chronicle, bare, summer);
            int withBees = reap(chronicle, kept, summer);
            int withRuin = reap(chronicle, burnt, summer);

            assertTrue(withBees > withoutBees,
                () -> "a skep beside the field must be worth raising — the wild bees do not work this ground, and "
                    + "bringing them is the whole of beekeeping (bare " + withoutBees + ", kept " + withBees + ")");
            assertEquals(withoutBees, withRuin,
                "a skep burnt out to nothing keeps no bees, and the field must reap exactly as the bare one does");
        } finally {
            jdbc.update("DELETE FROM crop_stand WHERE chunk_id IN (?,?,?)", bare, kept, burnt);
            jdbc.update("UPDATE construction_project SET integrity_percent=80 " +
                "WHERE object_id IN (SELECT id FROM world_object WHERE current_location_id IN (?,?,?))", bare, kept, burnt);
            for (int i = 0; i < fields.size(); i++)
                jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", were.get(i), fields.get(i));
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** The same crop, the same soil, the same rest, on every field — so only the bees can differ. */
    private void ripeField(UUID chunk, Instant now) {
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        jdbc.update("INSERT INTO field_soil (chunk_id, fertility, last_updated_at) VALUES (?,60,?) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET fertility=60, last_updated_at=EXCLUDED.last_updated_at",
            chunk, Timestamp.from(now));
        jdbc.update("DELETE FROM crop_stand WHERE chunk_id=?", chunk);
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested, tilled) " +
            "VALUES (?,?,'wild_grain',?,30,false,false)",
            UUID.randomUUID(), chunk, Timestamp.from(now.minus(Duration.ofDays(31))));
    }

    private void skep(UUID chunk, Instant now, int integrity) {
        UUID hive = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) " +
            "VALUES (?,'CONSTRUCTION','Bee skep','ACTIVE',?)", hive, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,'BEE_SKEP','COMPLETED',100,?,?,?)", hive, Timestamp.from(now), integrity, Timestamp.from(now));
    }

    private int reap(UUID chronicle, UUID chunk, Instant now) {
        int before = grainHeads(chronicle);
        String[] reaped = items.harvestCrop(chronicle, chunk, now);
        assertEquals("SUCCEEDED", reaped[0], () -> "the crop stands ripe and must reap: " + reaped[1]);
        return grainHeads(chronicle) - before;
    }
}
