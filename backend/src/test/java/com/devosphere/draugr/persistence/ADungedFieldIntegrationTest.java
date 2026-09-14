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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #77 V332 — a dunged field. Worn ground rested beside a sound manure pit comes back faster than ground rested bare,
 * so it reaps fuller after the same rest; a pit that has caved in feeds nothing. Skips without Docker.
 */
@SpringBootTest
class ADungedFieldIntegrationTest {

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

    private int grainHeads(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='wild_grain_head' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'",
            Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    /** Worked-out soil rested for the same span, a crop standing ripe on it, and nothing built on the ground. */
    private void ripeFieldOnWornSoil(UUID chunk, Instant now, int fallowDays) {
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        jdbc.update("INSERT INTO field_soil (chunk_id, fertility, last_updated_at) VALUES (?,10,?) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET fertility=10, last_updated_at=EXCLUDED.last_updated_at",
            chunk, Timestamp.from(now.minus(Duration.ofDays(fallowDays))));
        jdbc.update("DELETE FROM crop_stand WHERE chunk_id=?", chunk);
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested, tilled) " +
            "VALUES (?,?,'wild_grain',?,30,false,false)",
            UUID.randomUUID(), chunk, Timestamp.from(now.minus(Duration.ofDays(31))));
    }

    private void manurePit(UUID chunk, Instant now, int integrity) {
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Manure pit','ACTIVE',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) VALUES (?,'MANURE_PIT','COMPLETED',100,?,?,?)",
            pit, Timestamp.from(now), integrity, Timestamp.from(now));
    }

    private int reap(UUID chronicle, UUID chunk, Instant now) {
        int before = grainHeads(chronicle);
        String[] reaped = items.harvestCrop(chronicle, chunk, now);
        assertEquals("SUCCEEDED", reaped[0], () -> "the crop stands ripe and must reap: " + reaped[1]);
        return grainHeads(chronicle) - before;
    }

    @Test
    void groundRestedBesideAManurePitReapsFullerThanGroundRestedBare() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = Instant.now();

        // Three ordinary fields, none of them a floodplain, so the only difference between them is what stands there.
        List<UUID> fields = jdbc.queryForList(
            "SELECT c.id FROM world_chunk c WHERE c.biome='GRASSLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id AND es.site_kind ILIKE '%floodplain%') " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 3", UUID.class);
        assertEquals(3, fields.size(), "the world must have three ordinary fields to compare");
        UUID bare = fields.get(0), dunged = fields.get(1), caved = fields.get(2);

        // Fifteen days rested: bare ground is back to 40, below the low-fertility line; with dung at 4 a day it is back
        // to 70, above it. That line is the whole difference the heap makes.
        ripeFieldOnWornSoil(bare, now, 15);
        ripeFieldOnWornSoil(dunged, now, 15);
        ripeFieldOnWornSoil(caved, now, 15);
        manurePit(dunged, now, 100);
        manurePit(caved, now, 0);

        int fromBare = reap(chronicle, bare, now);
        int fromDunged = reap(chronicle, dunged, now);
        int fromCaved = reap(chronicle, caved, now);

        assertTrue(fromDunged > fromBare,
            () -> "rested the same fifteen days, a dunged field has come back and a bare one has not (dunged=" + fromDunged + ", bare=" + fromBare + ")");
        assertEquals(fromBare, fromCaved, "a manure pit that has caved in feeds the field nothing");
        assertTrue(fromBare > 0, "worn ground still gives something — a thinner stand, not a failure");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
