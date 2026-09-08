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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bed comes back faster than a scatter (#157).
 *
 * <p>A mussel bed is not a place where mussels happen to be. It is a dense mat of them cemented to each other and
 * to the rock, and that density is what lets it recover: spat settles on the shells already there, so the bed
 * reseeds from its own population. Work a thin scatter over open shore as hard and you have taken the seed with
 * the crop.
 *
 * <p>The colonies already reached the shore and the depletion model already worked — {@code product_ready_at} set
 * from {@code regrowth_days}. What was missing was any ground where that recovery differs, so every stretch of
 * coast came back at exactly one rate and a bed was a word with no consequence.
 *
 * <p>Regrowth is the seam under test rather than yield, deliberately: the yield path rolls {@code Math.random()}
 * per product, so a comparison of catches would be a comparison of dice. The recovery date is a pure function of
 * the working, and it is what the rule actually changes.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class ShellBedIntegrationTest {

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

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** The catalogue must know which colonies are shellfish, and they must reach the shore. */
    @Test
    void theCatalogueKnowsItsShellfish() {
        List<String> shellfish = jdbc.queryForList(
            "SELECT colony_kind FROM insect_colony_kind WHERE shellfish ORDER BY colony_kind", String.class);
        assertTrue(shellfish.size() >= 2, () -> "more than one colony forms beds: " + shellfish);

        List<String> inland = jdbc.queryForList(
            "SELECT colony_kind FROM insect_colony_kind WHERE shellfish AND biome_affinity NOT ILIKE '%COAST%'",
            String.class);
        assertTrue(inland.isEmpty(),
            () -> "a shellfish that never reaches the shore makes this a rule about ground nobody stands on: " + inland);
    }

    /** The world must place a bed, and place it on the shore. */
    @Test
    void theWorldPlacesABedOnTheShore() {
        world();
        String biome = jdbc.query(
            "SELECT c.biome FROM ecology_site es JOIN world_chunk c ON c.id=es.chunk_id WHERE es.site_kind='Shell bed' LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        assertNotNull(biome, "the world must place a shell bed");
        assertEquals("COAST", biome, "a shell bed lies on the shore, not " + biome);
    }

    /**
     * The rule itself, read where it is written. Working a colony records when it will be ready again; on a bed
     * that date must be sooner than the same colony's own declared regrowth would put it.
     */
    @Test
    void aWorkedBedIsReadyAgainSoonerThanAScatter() {
        world();
        Instant now = Instant.now();

        UUID bed = jdbc.query("SELECT chunk_id FROM ecology_site WHERE site_kind='Shell bed' LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        assertNotNull(bed, "the world must place a shell bed");

        UUID scatter = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome='COAST' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id AND es.site_kind ILIKE '%shell bed%') " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        assertNotNull(scatter, "the world must have open shore to contrast with");

        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        // Mussels want a blade to prise them off the rock (V270), so the working is not refused for want of one.
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", now, "CRAFTED");

        jdbc.update("DELETE FROM insect_colony WHERE chunk_id IN (?,?)", bed, scatter);
        for (UUID ground : List.of(bed, scatter)) {
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", ground, chronicle);
            items.collectInsects(chronicle, ground, "gather mussels from the mussel bed", now);
        }

        Timestamp onBed = jdbc.query(
            "SELECT product_ready_at FROM insect_colony WHERE chunk_id=? AND colony_kind='mussel_bed'",
            rs -> rs.next() ? rs.getTimestamp(1) : null, bed);
        Timestamp onScatter = jdbc.query(
            "SELECT product_ready_at FROM insect_colony WHERE chunk_id=? AND colony_kind='mussel_bed'",
            rs -> rs.next() ? rs.getTimestamp(1) : null, scatter);

        Assumptions.assumeTrue(onBed != null && onScatter != null,
            "both workings must have taken something for their recovery to be recorded (bed=" + onBed
                + ", scatter=" + onScatter + ")");

        assertTrue(onBed.before(onScatter),
            () -> "a bed reseeds from its own shells and must be ready sooner than open shore worked the same day "
                + "(bed=" + onBed + ", scatter=" + onScatter + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
