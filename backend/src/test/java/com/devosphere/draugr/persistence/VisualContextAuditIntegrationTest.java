package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Auditor must catch the corruptions that would make a place look wrong (#233).
 *
 * <p>What a Chronicle sees is <em>derived</em> from chunk, site and structure state, so the payload is only as
 * sound as that state — and it will faithfully report whatever it is given. A destroyed shelter that kept its
 * ground does not throw; it just goes on being scenery, and the place looks built long after it was pulled down.
 * That is the class of fault this checks: not a crash, a lie.
 *
 * <p>Each invariant gets its own corruption fixture, because an invariant nobody has ever seen fail is a claim,
 * not a check — and every fixture is rolled back so the corruption cannot leak into another test's world.
 *
 * <p>The Auditor is also asserted to be read-only: it reports without repairing, so a broken world stays broken
 * until someone fixes it deliberately. An auditor that quietly healed things would hide the very faults it exists
 * to surface.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class VisualContextAuditIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID ground() {
        return jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='GRASSLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
    }

    /** Raise a completed structure, run the corruption, read the report, then undo it all. */
    private String reportAfterCorrupting(String corruptionSql, Object... args) {
        world();
        assertTrue(auditor.inspect().consistent(),
            () -> "the world must be sound before it is corrupted: " + auditor.inspect().violations());

        UUID built = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Lean-to',?)",
            built, ground());
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) " +
            "VALUES (?,'LEAN_TO','COMPLETED',100,?)", built, Timestamp.from(now));
        try {
            jdbc.update(corruptionSql, java.util.stream.Stream.concat(
                java.util.Arrays.stream(args), java.util.stream.Stream.of(built)).toArray());
            var report = auditor.inspect();
            return String.join(" | ", report.violations());
        } finally {
            jdbc.update("DELETE FROM construction_project WHERE object_id=?", built);
            jdbc.update("DELETE FROM world_object WHERE id=?", built);
        }
    }

    /**
     * The two corruptions this file does NOT check, and why — recorded so nobody adds an invariant for them
     * believing it earns its place.
     *
     * <p>A site on ground that does not exist, and a standing structure with no ground at all, are both
     * <b>impossible</b>: {@code ecology_site.chunk_id} carries a foreign key to {@code world_chunk}, and the
     * {@code world_object} CHECK is {@code (location OR owner OR DESTROYED)}. I wrote invariants for both before
     * discovering that, and they could never have fired — a line that reads as coverage and is not.
     *
     * <p>This asserts the guarantees rather than the invariants, so if either constraint is ever relaxed the
     * decision surfaces here instead of quietly opening a hole the Auditor no longer watches.
     */
    @Test
    void theSchemaAlreadyForbidsWhatThisDoesNotCheck() {
        world();
        String siteFk = jdbc.queryForObject(
            "SELECT COALESCE(string_agg(pg_get_constraintdef(oid), ' '),'') FROM pg_constraint " +
            "WHERE conrelid='ecology_site'::regclass AND contype='f'", String.class);
        assertTrue(siteFk.contains("REFERENCES world_chunk"),
            () -> "a site is kept on real ground by a foreign key; without it the Auditor must check: " + siteFk);

        String objectCheck = jdbc.queryForObject(
            "SELECT COALESCE(string_agg(pg_get_constraintdef(oid), ' '),'') FROM pg_constraint " +
            "WHERE conrelid='world_object'::regclass AND conname='world_object_check'", String.class);
        assertTrue(objectCheck.contains("current_location_id IS NOT NULL")
                && objectCheck.contains("current_owner_id IS NOT NULL"),
            () -> "an active object is kept somewhere by this CHECK; without it the Auditor must check: " + objectCheck);
    }

    /** A destroyed build that kept its ground would go on being scenery. */
    @Test
    void aDestroyedStructureStillOccupyingGroundIsReported() {
        String violations = reportAfterCorrupting(
            "UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=now(), destroyed_cause='TEST' WHERE id=?");
        assertTrue(violations.contains("still occupy ground"),
            () -> "a destroyed structure that keeps its location must be reported — the place would look built "
                + "long after it was pulled down: " + violations);
    }

    /**
     * A living Chronicle standing on something that is not a place.
     *
     * <p>Reachable precisely because {@code current_location_id} references {@code world_object}, not
     * {@code world_chunk} — the foreign key is satisfied by <em>any</em> object, so a Chronicle can be sited on
     * the world root and the database will not object. Setting it NULL would be caught by the CHECK; pointing it
     * at the wrong kind of object is the hole that is actually open.
     */
    @Test
    void aChronicleStandingOnSomethingThatIsNotAPlaceIsReported() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID was = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        assertNotNull(was, "the Chronicle must start somewhere real");

        UUID notAPlace = jdbc.queryForObject(
            "SELECT id FROM world_object WHERE object_type='WORLD_ROOT' LIMIT 1", UUID.class);
        assertNotNull(notAPlace, "the world root is an object and not a chunk, which is what makes this reachable");

        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", notAPlace, chronicle);
        try {
            String violations = String.join(" | ", auditor.inspect().violations());
            assertTrue(violations.contains("not a place"),
                () -> "a living Chronicle with no ground must be reported: " + violations);
        } finally {
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", was, chronicle);
        }
        assertTrue(auditor.inspect().consistent(),
            () -> "and the world must be sound again once the corruption is undone: " + auditor.inspect().violations());
    }

    /**
     * The Auditor reports; it does not repair. A world that is broken must stay broken until someone fixes it
     * deliberately — an auditor that quietly healed things would hide the faults it exists to surface.
     */
    @Test
    void auditingChangesNothing() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID was = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);

        // Same reachable corruption as above: NULL would be refused by the CHECK, so the fault is a location
        // pointing at an object that is not a chunk.
        UUID notAPlace = jdbc.queryForObject(
            "SELECT id FROM world_object WHERE object_type='WORLD_ROOT' LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", notAPlace, chronicle);
        try {
            auditor.inspect();
            auditor.inspect();
            UUID stillWrong = jdbc.queryForObject(
                "SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
            assertTrue(notAPlace.equals(stillWrong),
                "inspecting twice must leave the fault exactly as it was — the Auditor is a witness, not a mender");
        } finally {
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", was, chronicle);
        }
    }
}
