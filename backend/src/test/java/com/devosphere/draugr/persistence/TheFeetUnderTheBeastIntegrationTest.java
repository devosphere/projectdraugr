package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feet under the beast (#106, V414) — the last genuine model gap on that ticket.
 *
 * <p>The ticket carried it unchanged across four comments: <em>"hoof care — no hoof or lameness state; routing it
 * to grooming would claim the COAT improved, which is a lie."</em> And the asking was half there already:
 * {@code is the goat lame} reached CHECK_STOCK, which reports hunger, thirst, fatigue and sickness — four real,
 * maintained states — and had nothing whatever to say about a foot. Its all-clear even read <em>"fed, watered,
 * rested and sound"</em>, and <b>sound</b> is the husbandry word for <b>not lame</b>: the prose was already
 * making the claim this test holds true.
 *
 * <p><b>What this holds that the routing test cannot</b> — the three halves of a closed chain:
 *
 * <ol>
 *   <li>the state MOVES on its own, and faster on the two kinds of ground that are hard on a foot;</li>
 *   <li>a bad foot COSTS something, in the one statement that charges a haul;</li>
 *   <li>the act PUTS IT BACK, and needs a blade to do it.</li>
 * </ol>
 *
 * <p>A state that only the first of those is true of is a column nothing advances; a state that only the first
 * two are true of is a punishment with no remedy.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheFeetUnderTheBeastIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    private UUID bondRaised;
    private UUID bladeGiven;

    @AfterEach
    void takeBackWhatThisTestMade() {
        // ONE world, ~340 tests. A tamed bond left behind is another test's herd and several of them count what
        // the Chronicle keeps; both are taken back by id rather than by a WHERE that could catch a stranger's.
        if (bondRaised != null) jdbc.update("DELETE FROM wildlife_bond WHERE id=?", bondRaised);
        if (bladeGiven != null) {
            jdbc.update("DELETE FROM item_instance WHERE object_id=?", bladeGiven);
            jdbc.update("DELETE FROM world_object WHERE id=?", bladeGiven);
        }
        bondRaised = null;
        bladeGiven = null;
    }

    private UUID aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(summary, "a living Chronicle is needed");
        return summary.id();
    }

    /** A beast with feet that want paring, by V414's own declared flag rather than by a species named here. */
    private void keepOneWithFeet(UUID me, int foot) {
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=? AND bond_stage='TAMED'", me);
        bondRaised = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO wildlife_bond (id, chronicle_id, population_id, bond_stage, trust_level, "
            + "  last_interaction_at, draft_thirst, draft_hunger, hoof_condition) "
            + "SELECT ?, ?, wp.id, 'TAMED', 90, now(), 5, 5, ? FROM wildlife_population wp "
            + " JOIN wildlife_species ws ON ws.species_key = wp.species_key "
            + " WHERE ws.feet_need_paring LIMIT 1",
            bondRaised, me, foot);
        Assumptions.assumeTrue(made != null && made == 1,
            "this world holds no population of a species with feet that need paring");
    }

    /** Ground of a named kind, for the tick's two hard cases. */
    private UUID groundWhere(String biomeList) {
        return jdbc.query("SELECT id FROM world_chunk WHERE biome IN (" + biomeList + ") ORDER BY grid_y, grid_x LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    }

    private int footOf(UUID bond) {
        Integer n = jdbc.queryForObject("SELECT hoof_condition FROM wildlife_bond WHERE id=?", Integer.class, bond);
        assertNotNull(n);
        return n;
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    @Test
    void aFootGoesOffOnItsOwnAndFasterOnGroundThatIsHardOnIt() {
        UUID me = aLivingChronicle();
        keepOneWithFeet(me, 100);

        // 1. IT MOVES AT ALL. Horn grows faster than easy ground wears it, which is why this is a chore and not
        //    an accident — a column that only fell when something went wrong would be a column nothing advances.
        UUID easy = groundWhere("'GRASSLAND','PLAINS','COAST'");
        Assumptions.assumeTrue(easy != null, "this world has no easy going to stand on");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", easy, me);
        items.advanceHoofCondition(Instant.now());
        int afterEasy = footOf(bondRaised);
        assertTrue(afterEasy < 100, "horn grows out even on good ground: " + afterEasy);

        // 2. AND FASTER WHERE THE GROUND IS HARD ON IT. Measured as a DELTA against the easy-going turn rather
        //    than against a constant, so the assertion survives anyone retuning the numbers.
        int easyLoss = 100 - afterEasy;
        UUID wet = groundWhere("'WETLAND','RIVER_BANK'");
        Assumptions.assumeTrue(wet != null, "this world has no standing wet to rot a foot in");
        jdbc.update("UPDATE wildlife_bond SET hoof_condition=100 WHERE id=?", bondRaised);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", wet, me);
        items.advanceHoofCondition(Instant.now());
        int wetLoss = 100 - footOf(bondRaised);
        assertTrue(wetLoss > easyLoss,
            () -> "standing wet softens horn and must cost more than good ground: " + wetLoss + " vs " + easyLoss);
    }

    @Test
    void nothingWithoutSuchFeetIsGivenTheChore() {
        UUID me = aLivingChronicle();
        // A bird, a fish, anything the flag is false for. Giving every animal in the world a hoof would put a
        // keeper's whole chore list onto a duck.
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=? AND bond_stage='TAMED'", me);
        UUID footless = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO wildlife_bond (id, chronicle_id, population_id, bond_stage, trust_level, "
            + "  last_interaction_at, hoof_condition) "
            + "SELECT ?, ?, wp.id, 'TAMED', 90, now(), 100 FROM wildlife_population wp "
            + " JOIN wildlife_species ws ON ws.species_key = wp.species_key "
            + " WHERE NOT ws.feet_need_paring LIMIT 1", footless, me);
        Assumptions.assumeTrue(made != null && made == 1, "this world holds no footless tameable population");
        try {
            items.advanceHoofCondition(Instant.now());
            assertEquals(100, (int) jdbc.queryForObject(
                "SELECT hoof_condition FROM wildlife_bond WHERE id=?", Integer.class, footless),
                "an animal with no such feet has nothing to pare and must not drift toward lame");
        } finally {
            jdbc.update("DELETE FROM wildlife_bond WHERE id=?", footless);
        }
    }

    @Test
    void theWelfareReadingNamesTheFootAndStopsClaimingSoundness() {
        UUID me = aLivingChronicle();
        keepOneWithFeet(me, 10);

        String prose = said("how are the animals");
        assertTrue(prose.contains("lame"),
            () -> "a lame beast must be named lame by the reading that walks the herd: " + prose);
        // The all-clear said "fed, watered, rested and sound" while nothing tracked a foot. It must not say that
        // of an animal that will not stand on one.
        assertTrue(!prose.contains("in good order"),
            () -> "the all-clear claims soundness, and this animal is not sound: " + prose);
    }

    @Test
    void paringNeedsABladeAndPutsTheFootBack() {
        UUID me = aLivingChronicle();
        keepOneWithFeet(me, 10);
        // Strip any blade first, so the refusal is measured rather than assumed.
        jdbc.update("UPDATE world_object SET lifecycle_state='ARCHIVED' WHERE current_owner_id=? AND id IN ("
            + "  SELECT i.object_id FROM item_instance i JOIN tool_profile t ON t.item_key=i.item_key "
            + "  WHERE t.tool_class='CUTTING')", me);

        String bare = said("pare the hooves");
        assertTrue(bare.contains("wants a blade") || bare.contains("will not take it back"),
            () -> "overgrown horn wants a blade and the refusal must say so: " + bare);
        assertEquals(10, footOf(bondRaised), "a refused paring must not improve the foot");

        // Now give them one, by the registry rather than by a name this test invents.
        bladeGiven = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id, created_at) "
            + "SELECT ?, 'ITEM', d.display_name, 'ACTIVE', ?, now() FROM item_definition d "
            + " JOIN tool_profile t ON t.item_key=d.item_key WHERE t.tool_class='CUTTING' LIMIT 1",
            bladeGiven, me);
        Assumptions.assumeTrue(made != null && made == 1, "this catalogue registers no CUTTING tool");
        jdbc.update("INSERT INTO item_instance (object_id, item_key, condition_state) "
            + "SELECT ?, t.item_key, 'SOUND' FROM tool_profile t JOIN item_definition d ON d.item_key=t.item_key "
            + " WHERE t.tool_class='CUTTING' LIMIT 1", bladeGiven);

        String done = said("pare the hooves");
        assertTrue(done.contains("pare") || done.contains("paring") || done.contains("horn"),
            () -> "the act must describe paring the foot: " + done);
        assertTrue(footOf(bondRaised) > 10,
            () -> "paring must put the foot back: still " + footOf(bondRaised));
    }

    @Test
    void aSoundFootIsRefusedRatherThanSpendingABladeOnNothing() {
        UUID me = aLivingChronicle();
        keepOneWithFeet(me, 100);
        String prose = said("pare the hooves");
        assertTrue(prose.contains("nothing on any of them") || prose.contains("nothing") && prose.contains("taking off"),
            () -> "a tight foot has nothing to take off, and saying so beats spending a tool's life: " + prose);
        assertEquals(100, footOf(bondRaised), "and it must not move a sound foot");
    }
}
