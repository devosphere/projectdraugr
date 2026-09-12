package com.devosphere.draugr.world.genesis;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mineral belongs to a place, not to a biome (#160, V306).
 *
 * <p>The ticket's coverage focus is that "obsidian, sulphur, kaolin, pumice, iron sand, ochre, and lime cannot be
 * gathered from unrelated ground", and that "no material is activated by a broad biome label alone". Before V306
 * every one of them could be: {@code biome_affinity} was the only gate there was, so obsidian came off any
 * mountain in the world. {@code mineral_province} binds the rare ones to the site they actually occur at, and
 * {@code gatherMineral} requires both.
 *
 * <p>Lives in the genesis package on purpose. The provinces name sites that {@code MARKER_SPECIFICATIONS} places,
 * and those two lists are a copy of each other — which is how this codebase gets its declared-but-ignored bugs.
 * Reading the Java list needs package access, so the contract between them is asserted here in both directions
 * rather than trusted.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class MineralProvinceIntegrationTest {

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

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** The exact gate gatherMineral applies, asked of the database directly: is this mineral in this ground? */
    private boolean inTheGroundAt(UUID chunk, String mineral) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM mineral_definition md WHERE md.mineral_key = ? " +
            "  AND md.biome_affinity ILIKE '%' || (SELECT biome FROM world_chunk WHERE id = ?) || '%' " +
            "  AND (NOT EXISTS (SELECT 1 FROM mineral_province p WHERE p.mineral_key = md.mineral_key) " +
            "       OR EXISTS (SELECT 1 FROM mineral_province p JOIN ecology_site s ON s.site_kind = p.site_kind " +
            "                   WHERE p.mineral_key = md.mineral_key AND s.chunk_id = ?)))",
            Boolean.class, mineral, chunk, chunk));
    }

    /**
     * The two lists must agree. A province naming a site the world never places gates its mineral to nowhere,
     * which reads in play exactly like the mineral having been deleted; a province claiming ground the marker
     * does not stand on is the same failure wearing a different mask.
     */
    @Test
    void everyProvinceNamesASiteTheWorldActuallyPlacesOnTheGroundItClaims() {
        List<Map<String, Object>> provinces = jdbc.queryForList("SELECT mineral_key, site_kind, site_biomes FROM mineral_province ORDER BY 1");
        assertFalse(provinces.isEmpty(), "V306 must have seeded provinces");

        List<String> wrong = new ArrayList<>();
        for (Map<String, Object> p : provinces) {
            String kind = (String) p.get("site_kind");
            WorldGenesisService.MarkerSpec spec = WorldGenesisService.MARKER_SPECIFICATIONS.stream()
                .filter(s -> "RESOURCE".equals(s.category()) && s.label().equals(kind))
                .findFirst().orElse(null);
            if (spec == null) {
                wrong.add(p.get("mineral_key") + " is gated to '" + kind + "', which the world places nowhere");
                continue;
            }
            Set<String> claimed = new LinkedHashSet<>(Arrays.asList(((String) p.get("site_biomes")).split(",")));
            Set<String> actual = new LinkedHashSet<>(Arrays.asList(spec.biomes()));
            if (!claimed.equals(actual))
                wrong.add(p.get("mineral_key") + " claims its " + kind + " stands on " + claimed + ", but the world stands it on " + actual);
        }
        assertTrue(wrong.isEmpty(), "mineral_province and MARKER_SPECIFICATIONS have drifted apart: " + wrong);
    }

    /**
     * And every gated mineral must be findable somewhere. This is the acceptance criterion the ticket states as
     * "enabling their approved site unlocks only the declared chains" read from the other side: a gate with no
     * site behind it does not make a mineral rare, it makes it imaginary.
     */
    @Test
    void everyGatedMineralHasItsProvinceStandingInTheWorld() {
        world();
        ecology.reconcile();

        List<String> unreachable = jdbc.queryForList(
            "SELECT DISTINCT p.mineral_key || ' is gated to a ' || p.site_kind || ', and the world holds none' " +
            "FROM mineral_province p WHERE NOT EXISTS (SELECT 1 FROM ecology_site s WHERE s.site_kind = p.site_kind) " +
            "ORDER BY 1", String.class);

        assertTrue(unreachable.isEmpty(),
            "these minerals are bound to geology that does not exist in this world, so nobody can ever dig them: " + unreachable);
    }

    /**
     * The pinned world must be able to receive the topology without being regenerated, and receiving it twice
     * must place nothing the second time.
     */
    @Test
    void reconcilingAnAlreadySeededWorldAddsWhatIsMissingAndThenNothing() {
        world();
        ecology.reconcile();
        int settled = jdbc.queryForObject("SELECT count(*) FROM ecology_site", Integer.class);

        WorldEcologyGenesisService.EcologySummary again = ecology.reconcile();
        assertEquals(0, again.siteCount(), "a reconciled world must gain nothing on a second pass");
        assertEquals(settled, (int) jdbc.queryForObject("SELECT count(*) FROM ecology_site", Integer.class),
            "reconciling must never move or duplicate a site that already stands");
    }

    /**
     * The behaviour itself: obsidian off a plain mountain is the exact thing the ticket forbids.
     *
     * <p>The refusal is asserted on its words, not just its outcome. A gate that answers "nothing but dirt"
     * teaches a player that the mineral does not exist; one that names the ground it belongs to teaches them what
     * to look for, and that difference is the whole value of binding a mineral to its geology.
     */
    @Test
    void obsidianComesOffAVolcanicFieldAndNotOffAnyMountain() {
        world();
        ecology.reconcile();

        UUID field = jdbc.queryForObject("SELECT chunk_id FROM ecology_site WHERE site_kind='Obsidian field' LIMIT 1", UUID.class);
        assertNotNull(field, "the world must hold an obsidian field for obsidian to come from");
        UUID barren = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='MOUNTAIN' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site s WHERE s.chunk_id=c.id AND s.site_kind='Obsidian field') " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(barren, "the world must hold an ordinary mountain to contrast with");

        // The gate, asked of the database exactly as gatherMineral asks it. Deterministic, unlike the rarity roll.
        assertTrue(inTheGroundAt(field, "obsidian_shard"), "obsidian must be in the ground at its own field");
        assertFalse(inTheGroundAt(barren, "obsidian_shard"), "obsidian must NOT be in the ground on an ordinary mountain");
        // And the ungated commons are untouched by any of this, which is what makes the slice additive.
        assertTrue(inTheGroundAt(barren, "field_stone"), "field stone stays findable on any ground that names it");
        assertTrue(inTheGroundAt(barren, "iron_ore"), "iron is not gated — bog iron really is found across whole country");

        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", ticks.current().simulatedAt(), "TEST_SEED");
        Instant now = ticks.current().simulatedAt();

        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", barren, chronicle);
        String[] refused = items.gatherMineral(chronicle, barren, "dig for obsidian here", now);
        assertEquals("FAILED", refused[0], "obsidian must not come off an ordinary mountain");
        assertTrue(refused[1].toLowerCase().contains("obsidian field"),
            () -> "the refusal must name the ground obsidian does come from, or it teaches nothing: " + refused[1]);

        // At the field it is a matter of searching, never of the wrong country. The rarity roll can refuse, and
        // that refusal must never be the province one.
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", field, chronicle);
        boolean found = false;
        for (int attempt = 0; attempt < 60 && !found; attempt++) {
            String[] dig = items.gatherMineral(chronicle, field, "dig for obsidian here", now);
            assertFalse(dig[1].toLowerCase().contains("does not come out of country like this"),
                () -> "at its own field, obsidian must never be refused for the ground: " + dig[1]);
            found = "SUCCEEDED".equals(dig[0]);
        }
        assertTrue(found, "obsidian must actually be gatherable at an obsidian field");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
