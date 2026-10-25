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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground a thing takes whole (#77, V432).
 *
 * <p>#77's last open criterion was footprint and capacity: whether a structure needs room, and whether two can
 * crowd one chunk. Nothing limited it — and one Java intent already did. {@code BUILD_PEN} refused a second pen
 * on the same ground: <i>"A pen already stands here; there is no room to ring another around this ground."</i>
 * That refusal was right and was the only one of its kind, counted against a single hard-coded
 * {@code construction_kind}. V432 promotes it to {@code construction_kind.takes_the_whole_ground}, read by both
 * {@code AssemblyService} and {@code ConstructionService.buildPen}, so there is <b>one rule rather than two</b>.
 *
 * <p><b>The rule is "at most one ring", not "nothing else here".</b> A pen ringing a byre is ordinary husbandry
 * and a shelter inside a fold is the point of a fold. Two rings around one piece of ground is the incoherent
 * thing, and that is all it refuses.
 *
 * <p><b>Two shortcuts were refused, and one of them I had already started on.</b> Not an area budget: a
 * footprint in paces for each of 117 kinds, gating every build in the game on arithmetic nobody asked for.
 * And not the existing {@code encloses} flag, which was my first instinct and is simply the wrong column —
 * <b>{@code ANIMAL_PEN} has {@code encloses = false}</b>, because that flag means a roofed space you get
 * <em>inside</em> (cabins, huts, byres, 32 kinds). Using it would have constrained the wrong thirty-two and
 * left the pen itself free.
 *
 * <p>The seven were chosen from the catalogue's own words: a <i>"stout walled yard"</i>, a <i>"ring of hurdles
 * set on open ground"</i>, two <i>"fenced stretch[es]"</i>, a <i>"broad shallow hollow"</i>, a <i>"hollow dug
 * wide and shallow"</i>, and the pen whose refusal is being generalised.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class GroundAThingTakesWholeIntegrationTest {

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

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var active = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(active, "a living Chronicle is needed");
        // Every case here hands the one shared Chronicle a fold's worth of rods and cordage, and the leftovers
        // pile up across cases until give() throws "cannot physically carry that load". None of these cases
        // tests a carry limit, so the Chronicle is given ample capacity rather than an order to run in.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=500000, direct_bulk_ml=500000, "
            + "maximum_single_lift_grams=500000 WHERE chronicle_id=?", active.id());
        return active.id();
    }

    /**
     * A chunk with nothing on it — and nothing BEGUN on it. A part-built structure has no world_object until it
     * stands, so a poultry run another case left at its first stage made its ground look bare; the next case
     * then raised its fold there, the half-built run counted as the one under way HERE, and the footprint gate
     * was rightly skipped. That is how CI failed this class with "the work wants a cutting edge".
     */
    private UUID bareGround() {
        UUID ground = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='GRASSLAND'"
          + " AND NOT EXISTS (SELECT 1 FROM world_object w WHERE w.current_location_id=c.id"
          + "                  AND w.lifecycle_state='ACTIVE')"
          + " AND NOT EXISTS (SELECT 1 FROM assembly_instance ai WHERE ai.location_id=c.id"
          + "                  AND ai.state='IN_PROGRESS')"
          + " ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(ground, "the world must have a chunk with nothing on it");
        return ground;
    }

    private void give(UUID chronicle, String key, String name, int n) {
        for (int i = 0; i < n; i++) items.createCarriedItem(chronicle, key, name, Instant.now(), "TEST_SEED");
    }

    /** Raise a goat fold here and return the ground it rings. */
    private UUID aFoldOnItsOwnGround(UUID chronicle) {
        UUID ground = bareGround();
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", ground, chronicle);
        give(chronicle, "hazel_rod", "Hazel rod", 10);
        give(chronicle, "fiber_cordage", "Processed fiber cordage", 6);
        var r = actions.resolve("build a goat fold", UUID.randomUUID());
        assertNotNull(r);
        assertEquals("SUCCEEDED", r.outcome(), () -> "the fold must go up on bare ground: " + r.perception());
        assertTrue(jdbc.queryForObject(
            "SELECT count(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id"
          + " JOIN construction_kind ck ON ck.project_kind=cp.project_kind"
          + " WHERE w.current_location_id=? AND cp.state='COMPLETED' AND w.lifecycle_state='ACTIVE'"
          + " AND ck.takes_the_whole_ground", Integer.class, ground) > 0,
            "and it must be standing as a ground-taking thing");
        return ground;
    }

    @Test
    void aSecondRingAroundTheSameGroundIsRefused() {
        UUID chronicle = chronicle();
        aFoldOnItsOwnGround(chronicle);
        give(chronicle, "hazel_rod", "Hazel rod", 10);
        give(chronicle, "fiber_cordage", "Processed fiber cordage", 6);
        give(chronicle, "dry_branch", "Dry branch", 6);

        var r = actions.resolve("build a poultry run", UUID.randomUUID());
        assertNotNull(r);
        assertEquals("FAILED", r.outcome(), () -> "two rings around one ground: " + r.perception());
        assertTrue(r.perception().contains("already takes this ground"),
            () -> "the refusal must say the ground is taken: " + r.perception());
        assertTrue(r.perception().contains("goat fold"),
            () -> "and name what is taking it, so the player knows what to move or undo: " + r.perception());
    }

    @Test
    void theJavaPenPathRefusesForTheSameReasonAndNamesTheFold() {
        // THE PROOF THAT THE TWO RULES ARE NOW ONE. BUILD_PEN used to count ANIMAL_PEN rows, so a goat fold was
        // invisible to it and a pen could be ringed around one. It now reads the same flag, and the refusal
        // names a structure its old query could never have seen.
        UUID chronicle = chronicle();
        aFoldOnItsOwnGround(chronicle);
        give(chronicle, "dry_branch", "Dry branch", 10);
        give(chronicle, "fiber_cordage", "Processed fiber cordage", 4);

        var r = actions.resolve("build a pen", UUID.randomUUID());
        assertNotNull(r);
        assertEquals("FAILED", r.outcome(), () -> "a pen around a fold is a second ring: " + r.perception());
        assertTrue(r.perception().contains("goat fold"),
            () -> "BUILD_PEN must now see the fold, which its old ANIMAL_PEN count could not: " + r.perception());
    }

    @Test
    void somethingThatTakesNoGroundOfItsOwnStillGoesUpBesideIt() {
        // THE POSITIVE CASE, and the one that keeps the rule from becoming "one structure per chunk". A stump,
        // a rack, a hearth — none of them rings the ground, and all of them belong beside a fold.
        UUID chronicle = chronicle();
        aFoldOnItsOwnGround(chronicle);
        give(chronicle, "dry_branch", "Dry branch", 4);
        give(chronicle, "stone_axe", "Stone axe", 1);

        var r = actions.resolve("build a woodworking stump", UUID.randomUUID());
        assertNotNull(r);
        assertFalse(r.perception().contains("already takes this ground"),
            () -> "a stump takes no ground of its own: " + r.perception());
        assertEquals("SUCCEEDED", r.outcome(), () -> "and must go up beside the fold: " + r.perception());
        assertTrue(auditor.inspect().consistent(),
            () -> "the world stays sound: " + auditor.inspect().violations());
    }

    @Test
    void theSameRingOnItsOwnGroundIsAllowed() {
        UUID chronicle = chronicle();
        aFoldOnItsOwnGround(chronicle);
        UUID elsewhere = bareGround();
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", elsewhere, chronicle);
        give(chronicle, "hazel_rod", "Hazel rod", 10);
        give(chronicle, "fiber_cordage", "Processed fiber cordage", 6);
        // The run's first stage wants an AXE. The footprint gate fires before the tool check, so the refusal
        // cases above do not need one — but this case has to actually SUCCEED, and a positive case that fails
        // for want of a tool would prove nothing about the ground.
        give(chronicle, "stone_axe", "Stone axe", 1);

        var r = actions.resolve("build a poultry run", UUID.randomUUID());
        assertNotNull(r);
        assertFalse(r.perception().contains("already takes this ground"),
            () -> "ground of its own is not crowded ground: " + r.perception());
        assertEquals("SUCCEEDED", r.outcome(), () -> "and the run goes up there: " + r.perception());
    }

    @Test
    void theSevenAreTheSevenAndThePenIsAmongThem() {
        chronicle();
        List<String> taking = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE takes_the_whole_ground ORDER BY 1", String.class);
        assertEquals(List.of("ANIMAL_PEN", "BULL_ISOLATION_YARD", "GOAT_FOLD", "MUD_WALLOW",
                             "POULTRY_RUN", "WATERFOWL_POOL", "WATERFOWL_RUN"), taking,
            "the seven chosen from the catalogue's own words, and nothing swept in with them");
    }

    @Test
    void itIsNotTheEnclosesFlagWearingANewName() {
        // The mistake this is most likely to be "simplified" into later. encloses means a roofed space you get
        // INSIDE — and ANIMAL_PEN, the one structure whose refusal started all this, has it FALSE. If the two
        // columns ever agree everywhere, 32 cabins and huts have been constrained by accident and the pen has
        // probably slipped free.
        chronicle();
        assertFalse(jdbc.queryForObject(
            "SELECT encloses FROM construction_kind WHERE project_kind='ANIMAL_PEN'", Boolean.class),
            "ANIMAL_PEN does not enclose — that is why encloses was the wrong flag for footprint");
        Integer differ = jdbc.queryForObject(
            "SELECT count(*) FROM construction_kind WHERE takes_the_whole_ground <> encloses", Integer.class);
        assertNotNull(differ);
        assertTrue(differ > 0,
            () -> "takes_the_whole_ground must not have collapsed into encloses: they differ on " + differ + " kinds");
    }
}
