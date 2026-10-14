package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Examining a thing you can see (#37).
 *
 * <p>{@code ExaminationService.resolveItem} searched the Chronicle's pack and nothing else, so examining
 * anything in the world fell through to a ground survey. Measured on a booted backend at midday, with a stone
 * knife in hand as the control:
 *
 * <pre>
 *   examine the stone knife -> "You take up the stone knife and look it over. It is whole and sound."
 *   examine the nettle      -> "You take a slow look at the ground and what stands on it..."
 *   examine the goat        -> the same ground survey
 *   examine my bronze axe   -> the same, for a thing the Chronicle does not own at all
 *   examine the lean-to     -> "You mark out a low shelter frame against the weather"  <-- BUILT ONE
 * </pre>
 *
 * <p>The first four are the shape this project calls its worst: the Chronicle asked about ONE thing and was
 * answered about ANOTHER, in the same even voice as every true line — the same defect as {@code eat the fish}
 * being answered with a mushroom. <b>The last one is worse, because it changed the world.</b> That guard had been
 * written once already for the question forms ({@code is the lean-to still good}) and left the imperative
 * perception verbs to fall through.
 *
 * <p>The subject is now looked for in the world as well, asked of the same tables the rest of the game reads:
 * {@code chunk_flora} plus the biome's growth for plants (exactly as the gathering rule does), a completed
 * {@code construction_project} for a structure, and a TAMED {@code wildlife_bond} for a beast. So what can be
 * examined is what can be acted on, and the two cannot drift apart.
 *
 * <p><b>The clock is pinned to midday</b>, because EXAMINE honestly refuses in the dark — *"It is too dark to see
 * the fine of it"* — and an unpinned run measures the hour rather than the rule. My own first pass at this axis
 * ran at night and I nearly recorded the darkness refusal as the defect.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class ExaminingAThingYouCanSeeIntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> given = new ArrayList<>();
    private UUID bondRaised;
    private Timestamp clockWas;

    @BeforeEach
    void pinTheClockToDaylight() {
        // The suite shares one simulation_clock, and EXAMINE refuses in the dark. Pinned and restored, because
        // drifting the shared world is the first shape of order-dependence this project recorded.
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = date_trunc('day', simulated_at) + interval '12 hours' WHERE id=1");
    }

    @AfterEach
    void takeBackWhatThisTestMade() {
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
        if (bondRaised != null) jdbc.update("DELETE FROM wildlife_bond WHERE id=?", bondRaised);
        for (UUID id : given) {
            boolean hasHistory = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM object_transition WHERE object_id=?)", Boolean.class, id));
            String state = jdbc.query("SELECT lifecycle_state FROM world_object WHERE id=?",
                rs -> rs.next() ? rs.getString(1) : null, id);
            if (state == null) continue;
            if (!hasHistory) {
                jdbc.update("DELETE FROM item_instance WHERE object_id=?", id);
                jdbc.update("DELETE FROM world_object WHERE id=?", id);
            } else if ("ACTIVE".equals(state)) {
                jdbc.update("UPDATE world_object SET lifecycle_state='ARCHIVED' WHERE id=?", id);
            }
        }
        given.clear();
        bondRaised = null;
        clockWas = null;
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

    private void carry(UUID me, String itemKey) {
        UUID id = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id, created_at) "
            + "SELECT ?, 'ITEM', d.display_name, 'ACTIVE', ?, now() FROM item_definition d WHERE d.item_key=?",
            id, me, itemKey);
        Assumptions.assumeTrue(made != null && made == 1, () -> "this catalogue has no " + itemKey);
        jdbc.update("INSERT INTO item_instance (object_id, item_key, condition_state) VALUES (?,?,'SOUND')", id, itemKey);
        given.add(id);
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    /** The ground survey — the answer that used to come back for every one of these. */
    private static boolean readsAsAGroundSurvey(String prose) {
        return prose.contains("slow look at the ground and what stands on it");
    }

    @Test
    void aPlantGrowingHereCanBeLookedOver() {
        UUID me = aLivingChronicle();
        // Asked of the SAME reach the gathering rule uses, so the test cannot name a plant the rule would not
        // find — it takes whatever this ground actually grows.
        String plant = jdbc.query(
            "SELECT replace(fd.flora_key,'_',' ') FROM flora_definition fd "
            + " WHERE fd.organism_type <> 'TREE' AND ("
            + "   EXISTS (SELECT 1 FROM chunk_flora cf JOIN world_object w ON w.id=? "
            + "            WHERE cf.chunk_id=w.current_location_id AND cf.flora_key=fd.flora_key AND cf.quantity > 0) "
            + "   OR fd.biome_affinity ILIKE '%' || (SELECT ch.biome FROM world_object w2 "
            + "        JOIN world_chunk ch ON ch.id=w2.current_location_id WHERE w2.id=?) || '%') LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, me, me);
        Assumptions.assumeTrue(plant != null, "this ground grows nothing to look at");

        String prose = said("examine the " + plant);
        assertFalse(readsAsAGroundSurvey(prose),
            () -> "\"examine the " + plant + "\" must be about the plant, not the ground: " + prose);
        assertTrue(prose.contains(plant), () -> "and must name it: " + prose);
    }

    @Test
    void aKeptAnimalCanBeLookedOver() {
        UUID me = aLivingChronicle();
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=? AND bond_stage='TAMED'", me);
        bondRaised = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO wildlife_bond (id, chronicle_id, population_id, bond_stage, trust_level, "
            + "  last_interaction_at, coat_condition, sickness) "
            + "SELECT ?, ?, wp.id, 'TAMED', 90, now(), 100, 0 FROM wildlife_population wp LIMIT 1",
            bondRaised, me);
        Assumptions.assumeTrue(made != null && made == 1, "this world holds no population to bond with");

        String beast = jdbc.queryForObject(
            "SELECT replace(wp.species_key,'_',' ') FROM wildlife_bond wb "
            + "JOIN wildlife_population wp ON wp.id=wb.population_id WHERE wb.id=?", String.class, bondRaised);
        String prose = said("examine the " + beast);
        assertFalse(readsAsAGroundSurvey(prose),
            () -> "\"examine the " + beast + "\" must be about the beast: " + prose);
        assertTrue(prose.contains(beast), () -> "and must name it: " + prose);
    }

    @Test
    void aCarriedThingStillBeatsAnythingInTheWorld() {
        // The pack is searched FIRST, deliberately: a knife in hand beats a knife lying in the grass. This is
        // the one case that already worked, held so the new world lookup cannot displace it.
        UUID me = aLivingChronicle();
        carry(me, "stone_knife");
        String prose = said("examine the stone knife");
        assertTrue(prose.contains("take up the stone knife"),
            () -> "a carried thing is taken up and looked over: " + prose);
    }

    @Test
    void aNamedThingThatIsNowhereIsSaidToBeNowhere() {
        UUID me = aLivingChronicle();
        // A real catalogue item the Chronicle does not have. Asked of the catalogue rather than named here,
        // because my first attempt at this test used "bronze sword" — and this catalogue has no sword at all,
        // so the sentence named nothing the game knows and the survey was the correct answer. The test phrase
        // was wrong, not the code.
        String absent = jdbc.query(
            "SELECT lower(d.display_name) FROM item_definition d WHERE d.item_key='bronze_axe'",
            rs -> rs.next() ? rs.getString(1) : null);
        Assumptions.assumeTrue(absent != null, "this catalogue has no bronze axe to miss");
        jdbc.update("UPDATE world_object SET lifecycle_state='ARCHIVED' WHERE current_owner_id=? AND id IN ("
            + " SELECT object_id FROM item_instance WHERE item_key='bronze_axe')", me);

        String prose = said("examine the " + absent);
        assertFalse(readsAsAGroundSurvey(prose),
            () -> "naming a thing that is nowhere must not be answered with a description of the marsh: " + prose);
        assertTrue(prose.contains("there is none here"), () -> "it must say so plainly: " + prose);
    }

    @Test
    void namingNoThingStillSurveysTheGround() {
        // The survey is the RIGHT answer to a sentence that names nothing, and is why the fallback exists. A
        // rule that demanded a subject before looking at anything would be a worse game.
        aLivingChronicle();
        String prose = said("examine the ground closely");
        assertTrue(readsAsAGroundSurvey(prose),
            () -> "\"examine the ground\" names no thing, so the survey is correct: " + prose);
    }
}
