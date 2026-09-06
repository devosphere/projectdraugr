package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything buildable must be in the registry every structural capability is gated on (#77/#219/#220).
 *
 * <p>{@code construction_kind} decides whether a build keeps the weather off you ({@code is_shelter}), whether
 * it wears and can be mended ({@code decays}), whether a roaring fire beside it can catch ({@code flammable}),
 * and whether it eases work ({@code is_workstation}). Fifteen assemblies named a {@code project_kind} with no
 * row in it at all, and because there is no foreign key on {@code construction_project.project_kind}, nothing
 * ever failed loudly — they simply fell through every registry-driven capability at once. A timber barn and a
 * log cabin never wore out; a wood store and a split-rail fence stood fireproof beside a roaring hearth.
 *
 * <p>That is the cost of the fix being right: decay and fire were moved off hand-written lists onto the registry,
 * which is correct, and it means anything absent from the registry quietly opts out of both. This keeps the
 * registry honest instead. Skips without Docker.
 */
@SpringBootTest
class ConstructionRegistryCompleteIntegrationTest {

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
    @Autowired com.devosphere.draugr.action.ChronicleActionService actions;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
    }

    @Test
    void everyAssemblyBuildsSomethingTheRegistryKnows() {
        world();
        List<String> orphans = jdbc.queryForList(
            "SELECT ad.assembly_key || ' builds ' || ad.construction_kind || ', which has no construction_kind row' " +
            "FROM assembly_definition ad WHERE ad.construction_kind IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind = ad.construction_kind) " +
            "ORDER BY 1", String.class);
        assertTrue(orphans.isEmpty(),
            "these builds are invisible to every registry-driven capability at once — they cannot shelter, wear, "
          + "be mended or catch fire, and nothing fails loudly because there is no foreign key: " + orphans);
    }

    /** Two assemblies answering to the same phrase means the one a Chronicle gets is arbitrary. */
    @Test
    void noTwoAssembliesAnswerToTheSamePhrase() {
        world();
        List<String> clashes = jdbc.queryForList(
            "SELECT a.assembly_key || ' and ' || b.assembly_key || ' both answer to \"' || ka.kw || '\"' " +
            "FROM assembly_definition a " +
            "JOIN LATERAL (SELECT trim(x) kw FROM unnest(string_to_array(a.keywords, ',')) x) ka ON true " +
            "JOIN assembly_definition b ON b.assembly_key > a.assembly_key " +
            "JOIN LATERAL (SELECT trim(y) kw FROM unnest(string_to_array(b.keywords, ',')) y) kb ON kb.kw = ka.kw " +
            "ORDER BY 1", String.class);
        assertTrue(clashes.isEmpty(),
            "assembly routing takes the longest keyword, so an exact tie is resolved arbitrarily: " + clashes);
    }

    /**
     * A Java hard intent runs BEFORE the assembly matcher, so an intent that claims an assembly's own keyword
     * shadows it completely. That is how "build a wattle wall" gave a wattle FENCE: BUILD_FENCE claimed the
     * phrase from before the wall's own assembly existed, and all four of its keywords were unreachable.
     *
     * <p>Checked against the classifier itself rather than a hand-kept list, so a new assembly keyword that
     * collides with an existing intent is caught the day it is added.
     *
     * <p>A collision is only a defect when the two paths build different things. Three intents deliberately
     * answer for an assembly by building the very same {@code construction_kind} in one shot — you get the
     * structure you asked for, just not stage by stage — so the exemption is that equality, not a list of
     * names: an intent that starts building something else is a finding no matter which assembly it steals.
     */
    private static final java.util.Map<String, String> INTENT_BUILDS = java.util.Map.of(
        "START_LEAN_TO", "LEAN_TO", "BUILD_FIRE_PIT", "STONE_FIRE_PIT", "BUILD_FENCE", "WATTLE_FENCE");

    @Test
    void noJavaIntentShadowsAnAssemblysOwnKeywords() throws Exception {
        world();
        java.lang.reflect.Method classify =
            com.devosphere.draugr.action.ChronicleActionService.class.getDeclaredMethod("classify", String.class);
        classify.setAccessible(true);
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(actions);

        List<String> shadowed = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> row : jdbc.queryForList(
                "SELECT ad.assembly_key, ad.construction_kind, trim(x) AS kw FROM assembly_definition ad, " +
                "unnest(string_to_array(ad.keywords, ',')) x WHERE length(trim(x)) >= 12")) {
            String phrase = (String) row.get("kw");
            String intent = ((Enum<?>) classify.invoke(target, phrase)).name();
            // UNKNOWN is what lets a phrase fall through to the assembly matcher at all.
            if ("UNKNOWN".equals(intent)) continue;
            if (INTENT_BUILDS.getOrDefault(intent, "").equals(row.get("construction_kind"))) continue;
            shadowed.add(row.get("assembly_key") + " :: \"" + phrase + "\" is taken by " + intent);
        }
        assertTrue(shadowed.isEmpty(),
            "a hard intent runs before the assembly matcher, so these assemblies cannot be reached by their own "
          + "words — the player asks for one thing and gets another: " + shadowed);
    }

    /** A shelter that cannot wear is a shelter that never needs mending; the two flags belong together. */
    @Test
    void everyShelterWearsAndTheStoneOnesDoNotBurn() {
        world();
        List<String> everlasting = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE is_shelter AND NOT decays ORDER BY 1", String.class);
        assertTrue(everlasting.isEmpty(),
            "a shelter that never wears never needs mending, and REPAIR_STRUCTURE reads against nothing: " + everlasting);

        // Sanity on the flammability classification: the earth and stone builds must not be marked to burn.
        List<String> burningStone = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE flammable AND project_kind IN " +
            "('STONE_FIRE_PIT','DRY_STONE_WALL','STONE_WALL_LOW','EARTH_BERM_WALL','DAUB_WALL','SNOW_SHELTER'," +
            " 'PIT_HOUSE','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','CLAY_LINED_HEARTH') ORDER BY 1", String.class);
        assertTrue(burningStone.isEmpty(), "earth, clay, snow and stone do not carry flame: " + burningStone);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
