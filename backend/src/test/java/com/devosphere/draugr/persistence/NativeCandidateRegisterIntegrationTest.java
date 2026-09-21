package com.devosphere.draugr.persistence;

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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The candidate register (#120) is a gate, not a list.
 *
 * <p>The world holds communities only of active candidates; an active candidate has passed all six reviews and names
 * the species it became; no release activates more than two; and taking a people's candidacy back out of ACTIVE makes
 * the database refuse their community. Skips without Docker.
 */
@SpringBootTest
class NativeCandidateRegisterIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    @Test
    void theWorldHoldsOnlyWhatReviewLetIn() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM native_community", Integer.class) > 0, "the world has its isles");
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_community n WHERE NOT EXISTS " +
            "(SELECT 1 FROM native_candidate c WHERE c.species_key=n.species_key AND c.status='ACTIVE')", Integer.class),
            "every community is of an active candidate");

        // The register may grow freely; the active world stays small.
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM native_candidate", Integer.class) >= 20, "the register is broad");
        List<Map<String, Object>> perRelease = jdbc.queryForList(
            "SELECT activated_in, COUNT(*) AS n FROM native_candidate WHERE status='ACTIVE' GROUP BY activated_in");
        for (Map<String, Object> r : perRelease)
            assertTrue(((Number) r.get("n")).intValue() <= 2, () -> "no more than two candidates activated in one release: " + perRelease);

        // An active candidate is a species with a declared cognition class matching its proposal.
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_candidate c LEFT JOIN cognition_profile p ON p.species_key=c.species_key " +
            "WHERE c.status='ACTIVE' AND (p.species_key IS NULL OR p.cognition_class <> c.proposed_class)", Integer.class));

        // Take the reedkin's candidacy back to review, and their community is refused.
        String answer = jdbc.execute((Connection c) -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("UPDATE native_candidate SET status='UNDER_REVIEW', species_key=NULL, activated_in=NULL WHERE candidate_key='reedkin'");
                try {
                    s.execute("UPDATE native_community SET species_key='reedkin' WHERE species_key='reedkin'");
                    return "accepted";
                } catch (SQLException e) {
                    return e.getMessage();
                }
            } finally {
                c.rollback();
                c.setAutoCommit(auto);
            }
        });
        assertTrue(answer.contains("#120"), () -> "a community without an active candidacy is refused: " + answer);
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM native_candidate WHERE candidate_key='reedkin'", String.class), "and nothing stuck");
    }

    /** #119's four, by name, and the one fact about each that decides anything. */
    private static final Map<String, String> ASKED_FOR_BY_119 = Map.of(
        "goblin_bands", "IIb",
        "ogre_lineages", "IIb",
        "cyclops_households", "III",
        "orc_clans", "III");

    /**
     * #119: goblin, ogre, cyclops and orc are registered as peoples, and the register says what each one waits on.
     *
     * <p>They were registered by V355 and given long cards in 12.1-Native-Peoples, and three of the four rows in
     * the table said, in full, "Defined in #119." The cards carry the fact that decides anything — the system that
     * must exist before this people could be placed at all — and the register, which is what a later cycle reads to
     * choose the next culture, carried none of it. A register of names is a catalogue. This asserts it is a plan.
     */
    @Test
    void everyCandidacySaysWhatItIsWaitingOn() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }

        // The four #119 asked for, at the tier it asked for, as PEOPLE and not as monsters.
        for (Map.Entry<String, String> asked : ASKED_FOR_BY_119.entrySet()) {
            Map<String, Object> row = jdbc.queryForMap(
                "SELECT proposed_tier, proposed_class, status, ground, home_form, note, needs_first " +
                "FROM native_candidate WHERE candidate_key=?", asked.getKey());
            assertEquals(asked.getValue(), row.get("proposed_tier"), () -> asked.getKey() + " is registered at the tier #119 gave it");
            assertEquals("PEOPLE", row.get("proposed_class"), () -> asked.getKey() + " is a people, never a population to be fought and looted");
            assertEquals("CANDIDATE", row.get("status"), () -> asked.getKey() + " is not placed: #115 caps the release at two cultures");
            assertTrue(((String) row.get("ground")).contains("_") || ((String) row.get("ground")).length() > 6,
                () -> asked.getKey() + " names the ground it would stand on: " + row.get("ground"));
            for (String column : List.of("home_form", "note", "needs_first"))
                assertTrue(((String) row.get(column)).length() > 40,
                    () -> asked.getKey() + " answers for its own " + column + " rather than pointing at a document: " + row.get(column));
        }

        // And nothing anywhere in the register is a name with a shrug attached.
        List<Map<String, Object>> silent = jdbc.queryForList(
            "SELECT candidate_key, needs_first FROM native_candidate WHERE needs_first LIKE 'Unreviewed%' ORDER BY candidate_key");
        assertTrue(silent.isEmpty(), () -> "every candidacy names the system standing between it and the world: " + silent);
        assertEquals(2, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_candidate WHERE needs_first LIKE 'Nothing:%'", Integer.class),
            "exactly the placed peoples are waiting on nothing");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM native_candidate WHERE (status='ACTIVE') <> (needs_first LIKE 'Nothing:%')", Integer.class),
            "a people is placed when it is waiting on nothing, and waiting on nothing when it is placed");

        // The teeth, on a name #119 introduced: the register is a gate, not a list. Founding a community for a
        // candidacy still under review is refused by the database, whatever the design document says about it.
        String answer = jdbc.execute((Connection c) -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                try {
                    s.execute("UPDATE native_community SET species_key='goblin_bands' WHERE species_key='reedkin'");
                    return "accepted";
                } catch (SQLException e) {
                    return e.getMessage();
                }
            } finally {
                c.rollback();
                c.setAutoCommit(auto);
            }
        });
        assertTrue(answer.contains("#120"), () -> "a candidacy that has not passed review cannot be founded: " + answer);
    }
}
