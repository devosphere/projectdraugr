package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A carcass must give meat in proportion to the animal (#54).
 *
 * <p>The yield was a substring list: a key containing "bear" or "elk" gave 4, "deer" or "boar" gave 3, and
 * <b>everything else in the world gave 1</b>. An aurochs, a cave bear, a moose-sized mud tortoise and a
 * crocodilian each yielded a single piece of meat — the same as a bank vole — while a red deer yielded three
 * because of the letters in its name. {@code wildlife_species.size_tier} has been accurate for all 204 species
 * the whole time and nothing read it.
 *
 * <p>Exercised through the private reader by reflection, because it is a pure function of the species and the
 * registry; killing an aurochs to prove it would mean asserting on combat, which is opaque and expensive.
 * Skips without Docker.
 */
@SpringBootTest
class MeatByAnimalSizeIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /**
     * The service is a Spring proxy, and a PRIVATE method invoked reflectively on the proxy runs against the
     * proxy's own fields — which are null, because only public calls are delegated to the target. Unwrap first,
     * or {@code this.jdbc} is null and the reader cannot reach the registry it now reads.
     */
    private int meatFor(String species) throws Exception {
        WildlifeEncounterService target = org.springframework.test.util.AopTestUtils.getTargetObject(wildlife);
        var m = WildlifeEncounterService.class.getDeclaredMethod("meatFor", String.class);
        m.setAccessible(true);
        return (int) m.invoke(target, species);
    }

    @Test
    void aBiggerAnimalYieldsMoreMeat() throws Exception {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }

        // One real species of each tier, taken from the registry rather than named here, so this cannot drift.
        for (String tier : List.of("HUGE", "LARGE", "MEDIUM", "SMALL", "TINY")) {
            String species = jdbc.queryForObject(
                "SELECT species_key FROM wildlife_species WHERE size_tier=? ORDER BY species_key LIMIT 1", String.class, tier);
            assertNotNull(species, "the registry must hold a " + tier + " species");
            int meat = meatFor(species);
            assertTrue(meat > 0, tier + " " + species + " must yield some meat");
        }

        String huge = jdbc.queryForObject("SELECT species_key FROM wildlife_species WHERE size_tier='HUGE' ORDER BY species_key LIMIT 1", String.class);
        String tiny = jdbc.queryForObject("SELECT species_key FROM wildlife_species WHERE size_tier='TINY' ORDER BY species_key LIMIT 1", String.class);
        String large = jdbc.queryForObject("SELECT species_key FROM wildlife_species WHERE size_tier='LARGE' ORDER BY species_key LIMIT 1", String.class);
        String medium = jdbc.queryForObject("SELECT species_key FROM wildlife_species WHERE size_tier='MEDIUM' ORDER BY species_key LIMIT 1", String.class);

        assertTrue(meatFor(huge) > meatFor(large), huge + " must be worth more than " + large);
        assertTrue(meatFor(large) > meatFor(medium), large + " must be worth more than " + medium);
        assertTrue(meatFor(medium) > meatFor(tiny), medium + " must be worth more than " + tiny);

        // The specific case that made this obvious: the biggest animal in the world was worth one piece of meat,
        // exactly as much as the smallest, because neither key contained "bear", "elk", "deer" or "boar".
        assertTrue(meatFor("aurochs") >= 8, "an aurochs is an aurochs; it gave 1 before, the same as a vole");
        assertTrue(meatFor("mud_tortoise_colossus") >= 8, "so did a creature with 'colossus' in its name");

        // And the values the old list got right are preserved, so nothing that worked is quietly rebalanced.
        assertEquals(4, meatFor("brown_bear"), "a bear still gives 4");
        assertEquals(4, meatFor("elk"), "an elk still gives 4");
        assertEquals(4, meatFor("red_deer"), "a red deer is LARGE, and still gives what it gave");
        assertEquals(3, meatFor("wild_boar"), "a boar still gives 3");

        // A species the registry does not know falls back to the old reading rather than to nothing.
        assertEquals(4, meatFor("some_unlisted_bear"), "an uncatalogued species keeps the old substring reading");
        assertEquals(1, meatFor("some_unlisted_thing"), "and an unrecognised one still gives the old default");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
