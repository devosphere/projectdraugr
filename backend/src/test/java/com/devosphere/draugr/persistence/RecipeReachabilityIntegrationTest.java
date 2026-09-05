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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every recipe must be reachable by its own words (#62/#73).
 *
 * <p>The matcher is two-axis: a phrase must classify to the SAME category the process declares, and carry one of
 * its keywords and one of its subject terms. Five recipes declared a category their own canonical phrases never
 * classify to, so the category axis rejected every sentence that would have named them — and two of those five
 * were worse than silent, resolving "shape a stone mortar" to MIX_MORTAR and handing a Chronicle builder's
 * mortar when they asked for a stone bowl to grind in.
 *
 * <p>The Auditor's own self-classify gate does not catch this: it asks only that a keyword CONTAINS a term of
 * the declared category, not that the term WINS the weighted argmax. This closes that gap as a standing
 * invariant, so a new recipe cannot be added unroutable. Skips without Docker.
 */
@SpringBootTest
class RecipeReachabilityIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /**
     * The classification {@link com.devosphere.draugr.routing.ActivityClassifier} actually performs: sum
     * {@code category_term} weights over whole-word matches and take the highest score, breaking a tie by
     * <b>{@code activity_category.precedence}, lowest first</b> — HUNT 1, ACQUIRE 2, PROCESS 3, CRAFT 4,
     * CONSTRUCT 5.
     *
     * <p>The precedence tie-break is the part that is easy to get wrong, and getting it wrong invents findings:
     * on a tie CRAFT beats CONSTRUCT, so "carve a cage frame" (carve CRAFT 2 vs frame CONSTRUCT 2) and "lash a
     * snowshoe" (lash is 2 in both) classify CRAFT and were correct all along.
     */
    @Test
    void everyRecipeCanBeReachedByItsOwnPhrases() {
        if (worldGenesis.current() == null) worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());

        List<String> unreachable = jdbc.queryForList(
            "WITH kw AS (" +
            "  SELECT mp.process_key, mp.category_key, trim(x) AS phrase " +
            "  FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) x " +
            "  WHERE mp.category_key IS NOT NULL), " +
            "classified AS (" +
            "  SELECT kw.process_key, kw.category_key, " +
            "         (SELECT ct.category_key FROM category_term ct " +
            "          JOIN activity_category ac ON ac.category_key = ct.category_key " +
            "          WHERE kw.phrase ~ ('\\m' || lower(ct.term) || '\\M') " +
            "          GROUP BY ct.category_key, ac.precedence " +
            "          ORDER BY SUM(ct.weight) DESC, ac.precedence ASC LIMIT 1) AS lands_on " +
            "  FROM kw) " +
            "SELECT process_key || ' declares ' || category_key || ' but every one of its phrases classifies elsewhere' " +
            "FROM classified GROUP BY process_key, category_key " +
            "HAVING bool_and(lands_on IS DISTINCT FROM category_key) AND bool_or(lands_on IS NOT NULL) " +
            "ORDER BY 1", String.class);

        assertTrue(unreachable.isEmpty(),
            "these recipes cannot be reached by the words they were given — the category axis rejects every "
          + "sentence that would name them, so they exist and can never be run: " + unreachable);
    }

    /** The specific collision behind the worst of the five: a stone bowl and builder's mortar share a word. */
    @Test
    void aStoneMortarIsNotBuildersMortar() {
        if (worldGenesis.current() == null) worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());

        // The stone bowl must not claim the bare word, or it competes with mixing mortar on equal length and
        // the winner is arbitrary.
        List<String> bare = jdbc.queryForList(
            "SELECT trim(x) FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) x " +
            "WHERE mp.process_key='shape_stone_mortar' AND trim(x) = 'mortar'", String.class);
        assertTrue(bare.isEmpty(), "the stone bowl must not carry the bare homonym as a keyword");

        // And it must still be the longer, more specific match for its own phrasing.
        Integer stoneMortarKw = jdbc.queryForObject(
            "SELECT MAX(length(trim(x))) FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) x " +
            "WHERE mp.process_key='shape_stone_mortar' AND 'shape a stone mortar' ~ ('\\m'||lower(trim(x))||'\\M')",
            Integer.class);
        Integer mixMortarKw = jdbc.queryForObject(
            "SELECT MAX(length(trim(x))) FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) x " +
            "WHERE mp.process_key='mix_mortar' AND 'shape a stone mortar' ~ ('\\m'||lower(trim(x))||'\\M')",
            Integer.class);
        assertTrue(stoneMortarKw != null && (mixMortarKw == null || stoneMortarKw > mixMortarKw),
            "\"shape a stone mortar\" must match the stone bowl on a longer keyword than builder's mortar");
    }

    /** The looser gate the Auditor already enforces must still pass — this test tightens, never replaces it. */
    @Test
    void theAuditorsOwnGateStillPasses() {
        if (worldGenesis.current() == null) worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
        Integer miscategorised = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process mp WHERE NOT EXISTS (" +
            "  SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k " +
            "  JOIN category_term ct ON ct.category_key = mp.category_key " +
            "  WHERE ' ' || btrim(k) || ' ' LIKE '% ' || ct.term || ' %' " +
            "     OR ' ' || ct.term  || ' ' LIKE '% ' || btrim(k) || ' %')", Integer.class);
        assertEquals(0, miscategorised);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
