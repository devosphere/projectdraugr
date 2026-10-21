package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eleven that had nowhere to go (#108, V426).
 *
 * <p>The audit on this story measured all 47 names live: 19 present by key, 6 reachable under another name, 5
 * answered by the Java {@code BUILD_PEN} intent, 6 wrongly routed (V424) and <b>eleven that reached nothing at
 * all</b>. These are the eleven — and each was checked against the catalogue before anything was built, because
 * that question has changed the answer five times running: <b>a key absent from the catalogue is only a gap if
 * nothing else owns the phrase.</b>
 *
 * <p><b>Six are built</b>, each needing something the catalogue has nowhere to put:
 *
 * <ul>
 *   <li>{@code nesting_box} — the only place in the catalogue where a bird <em>lays</em> ({@code shelters_birth}).</li>
 *   <li>{@code poultry_run} — an <em>outdoor</em> enclosure; {@code poultry_coop} is the enclosed night shelter.</li>
 *   <li>{@code duck_house} — a wetland shelter. #108 is explicit: "not a dry coop copy".</li>
 *   <li>{@code waterfowl_run} — an enclosure with open water <em>in</em> it.</li>
 *   <li>{@code yak_shelter} — <b>the only stock shelter that does not give shade.</b> Every other one does, and
 *       shade is the wrong answer on a cold hillside; this one encloses to break a wind.</li>
 *   <li>{@code double_gate_entry} — a biosecurity airlock. {@code simple_gate} is one gate, which is what this
 *       deliberately is not.</li>
 * </ul>
 *
 * <p><b>Five are renamed</b>, because something already does the job and a second one would be a token: a donkey
 * shelter is a stable, a buffalo shed is a byre, a loading platform is the loading ramp, a rotational grazing
 * gate is a gate, and a tool wash station is the washing place.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheElevenThatHadNowhereToGoIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final List<String> BUILT = List.of(
        "nesting_box", "poultry_run", "duck_house", "waterfowl_run", "yak_shelter", "double_gate_entry");

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

    private void aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertNotNull(chronicles.active() != null ? chronicles.active() : chronicles.awaken(),
            "a living Chronicle is needed");
    }

    private String intentOf(String text) {
        // A LIVING CHRONICLE BEFORE EACH. A routing assertion costs simulated time like any other action, and a
        // sibling class was stopped by CI at the eleventh phrase of one loop with "No living Chronicle exists".
        aLivingChronicle();
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    @Test
    void allElevenNowReachAStructure() {
        aLivingChronicle();
        for (String said : List.of("build a nesting box", "build a poultry run", "build a duck house",
                                   "build a waterfowl run", "build a donkey shelter", "build a buffalo shed",
                                   "build a yak shelter", "build a loading platform",
                                   "build a rotational grazing gate", "build a double gate entry",
                                   "build a tool wash station")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' reached nothing before V426: -> " + intentOf(said));
        }
    }

    @Test
    void theShorterWayOfSayingItWorksToo() {
        aLivingChronicle();
        for (String said : List.of("build a nest box", "build a chicken run", "build a duck run",
                                   "build a yak shed", "build a double gate", "build a donkey shed",
                                   "build a grazing gate", "build a wash station")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' is how a player says it: -> " + intentOf(said));
        }
    }

    @Test
    void nothingThatAlreadyAnsweredWasDisplaced() {
        // The five renames put words onto structures that already had their own, and longest-keyword-wins means
        // a new name can shadow an older one. These are the ones at risk.
        aLivingChronicle();
        for (String said : List.of("build a horse stable", "build a cattle byre", "build a simple gate",
                                   "build a gate", "build a washing place", "build a loading ramp",
                                   "build a poultry coop", "build a waterfowl pool", "build a mud wallow")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' worked before V426 and must keep working: -> " + intentOf(said));
        }
    }

    @Test
    void aYakShelterIsTheOneThatDoesNotGiveShade() {
        // THE WHOLE ARGUMENT FOR IT EXISTING. Every other stock shelter in the catalogue gives shade; on a cold
        // hillside that is the opposite of help, so this one encloses to break a wind instead. If it ever starts
        // giving shade it has become a cattle byre, and it should be renamed onto one rather than kept as a
        // second thing for the same job.
        aLivingChronicle();
        assertFalse(jdbc.queryForObject(
            "SELECT gives_shade FROM construction_kind WHERE project_kind='YAK_SHELTER'", Boolean.class),
            "a yak shelter that gives shade is a cattle byre");
        assertTrue(jdbc.queryForObject(
            "SELECT encloses AND is_shelter FROM construction_kind WHERE project_kind='YAK_SHELTER'", Boolean.class),
            "it must enclose and shelter, which is how a structure breaks a wind here");

        Integer shaded = jdbc.queryForObject(
            "SELECT count(*) FROM construction_kind WHERE shelters_stock AND NOT gives_shade AND NOT encloses",
            Integer.class);
        assertNotNull(shaded);
    }

    @Test
    void aNestingBoxIsWhereABirdLays() {
        // The flag is the point: shelters_birth has a live reader, so a nesting box changes what the world does
        // rather than only what the catalogue lists. SMALL because the CHECK insists on a size, and it is right
        // to — a shelter whose size is unknown cannot be matched against the creature trying to get into it.
        aLivingChronicle();
        assertTrue(jdbc.queryForObject(
            "SELECT shelters_birth FROM construction_kind WHERE project_kind='NESTING_BOX'", Boolean.class),
            "a nesting box is where laying happens");
        assertEquals("SMALL", jdbc.queryForObject(
            "SELECT shelters_up_to_size FROM construction_kind WHERE project_kind='NESTING_BOX'", String.class),
            "sized for a bird, not a cow");
    }

    @Test
    void notOneOfTheSixIsACatalogueToken() {
        aLivingChronicle();
        for (String key : BUILT) {
            Boolean live = jdbc.queryForObject(
                "SELECT (ck.is_shelter OR ck.is_workstation OR ck.encloses OR ck.is_barrier OR ck.holds_fire"
              + " OR ck.crosses_soft_ground OR ck.holds_open_water OR ck.shelters_birth OR ck.aids_mounting"
              + " OR ck.shelters_stock OR ck.shelters_gear OR ck.gives_shade OR ck.separates_dangerous"
              + " OR ck.eases_going > 0 OR ck.barrier_strength > 0)"
              + " FROM assembly_definition d JOIN construction_kind ck ON ck.project_kind = d.construction_kind"
              + " WHERE d.assembly_key = ?", Boolean.class, key);
            assertNotNull(live, () -> key + " has no construction_kind at all");
            assertTrue(live, () -> key + " declares no capability and is a token with a display name");
        }
    }

    @Test
    void everyOneOfTheSixIsVerifiedStagedAndMadeOfRealThings() {
        aLivingChronicle();
        for (String key : BUILT) {
            assertEquals("VERIFIED", jdbc.queryForObject(
                "SELECT review_state FROM assembly_definition WHERE assembly_key = ?", String.class, key),
                () -> key + " is unbuildable while it is not VERIFIED");

            Integer stages = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_stage WHERE assembly_key = ?", Integer.class, key);
            assertNotNull(stages);
            assertTrue(stages > 0, () -> key + " has no stages, so there is nothing to run");

            Integer unsourced = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_stage s WHERE s.assembly_key = ? AND s.cure_minutes = 0"
              + " AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key)",
                Integer.class, key);
            assertEquals(0, unsourced, () -> key + " has a stage that needs nothing to build it");

            Integer unreal = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key"
              + " WHERE s.assembly_key = ? AND NOT EXISTS (SELECT 1 FROM item_definition i WHERE i.item_key = r.item_key)",
                Integer.class, key);
            assertEquals(0, unreal, () -> key + " is built from a material that does not exist");
        }
    }

    @Test
    void eachRenamedNameReachesExactlyOneThing() {
        aLivingChronicle();
        for (String name : List.of("donkey shelter", "buffalo shed", "loading platform", "grazing gate",
                                   "wash station", "nesting box", "poultry run", "duck house",
                                   "waterfowl run", "yak shelter", "double gate entry")) {
            Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_definition WHERE ',' || replace(keywords,', ',',') || ',' LIKE ?",
                Integer.class, "%," + name + ",%");
            assertNotNull(n);
            assertEquals(1, n, () -> "'" + name + "' names one structure, not " + n);
        }
    }

    @Test
    void noneOfTheNewNamesTookASentenceFromARecipe() {
        aLivingChronicle();
        List<String> stolen = jdbc.queryForList(
            "SELECT DISTINCT v.w || ' <- ' || mp.process_key FROM (VALUES ('nesting box'),('nest box'),"
          + "('poultry run'),('chicken run'),('duck house'),('duck run'),('yak shelter'),('double gate'),"
          + "('donkey shelter'),('buffalo shed'),('loading platform'),('grazing gate'),('wash station')) v(w),"
          + " material_process mp, unnest(string_to_array(mp.keywords,',')) k"
          + " WHERE ' ' || lower(trim(k)) || ' ' LIKE '% ' || v.w || ' %'", String.class);
        assertTrue(stolen.isEmpty(), () -> "a structure name collides with a recipe: " + stolen);
    }

    @Test
    void nothingFullyStagedIsLeftUnreachable() {
        aLivingChronicle();
        List<String> stranded = jdbc.queryForList(
            "SELECT ad.assembly_key FROM assembly_definition ad WHERE ad.review_state <> 'VERIFIED'"
          + " AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = ad.assembly_key)", String.class);
        assertTrue(stranded.isEmpty(), () -> "fully staged and still unreachable: " + stranded);
    }
}
