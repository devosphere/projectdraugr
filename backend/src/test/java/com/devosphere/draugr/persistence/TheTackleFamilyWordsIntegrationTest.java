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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The family words for fishing tackle (#37, V417).
 *
 * <p>Found by sweeping the fishing chain end to end. Measured live: {@code make a hook}, {@code make a creel} and
 * {@code make a fishing line} all reached nothing, while the catalogue holds four fish hooks, two fishing lines
 * and a creel — each reachable only by naming its material or its maker's verb outright. {@code carve a hook}
 * worked the whole time, because that exact phrase is declared.
 *
 * <p><b>A family word fails if EITHER half is missing, and this slice contains both directions</b> — which is
 * why the guards check the halves together rather than counting keywords:
 *
 * <ul>
 *   <li>{@code hook} — three processes already carried it as a SUBJECT and none as a keyword;</li>
 *   <li>{@code creel} — the same, on one process;</li>
 *   <li>{@code fishing line} — neither half anywhere.</li>
 * </ul>
 *
 * <p>V416's {@code wrap} was the mirror of the first two: ten keywords declared and no bare subject anywhere, so
 * it reached nothing while the keyword count looked perfectly healthy. Checking one half tells you nothing.
 *
 * <p><b>One of the three needs a change this migration does not contain.</b> {@code make a fishing line} is
 * claimed by the FISH intent — the sentence contains "fishing" — and no process keyword can outrank a Java
 * intent. The guard that frees it is in PR #774 ({@code !MAKING_SOMETHING} on the FISH rule). Verified by
 * applying that guard temporarily alongside this data: the pair answers <em>"Those words fit more than one piece
 * of work — twist a bast fishing line, or twist a sinew fishing line"</em>. So this test asserts the DATA for
 * that word and the behaviour only for the two that do not depend on #774, which keeps it honest whichever
 * merges first.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheTackleFamilyWordsIntegrationTest {

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

    private String said(String text) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        if (chronicles.active() == null) assertNotNull(chronicles.awaken(), "a living Chronicle is needed");
        var r = actions.resolve(text, java.util.UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    private static boolean readsAsAMiss(String prose) {
        return prose.contains("the way to make what you intend does not come")
            || prose.contains("cannot find the step that would take it further")
            || prose.contains("the stuff of it is willing enough")
            || prose.contains("each one stops at the same place")
            || prose.contains("no method for what you meant")
            || prose.contains("beyond what your hands and knowledge");
    }

    @Test
    void thePlainWordForAHookAndACreelReachesTheirRecipes() {
        String hook = said("make a hook");
        assertFalse(readsAsAMiss(hook), () -> "\"make a hook\" must reach the four fish hooks: " + hook);

        String creel = said("make a creel");
        assertFalse(readsAsAMiss(creel), () -> "\"make a creel\" must reach the creel: " + creel);
    }

    @Test
    void theMakersOwnVerbStillWorks() {
        // `carve a hook` is declared outright and worked the whole time. A family word that broke the specific
        // phrase would be a worse trade than the gap it closed.
        String carved = said("carve a hook");
        assertFalse(readsAsAMiss(carved), () -> "the declared phrase must keep working: " + carved);
    }

    @Test
    void bothHalvesOnTheSameProcessForEveryWordThisSliceAdds() {
        // THE GUARD V416's FIRST CUT LACKED. The matcher wants a keyword AND a subject on the SAME process;
        // either alone is inert, and a keyword count cannot tell you which is missing. All three words here were
        // broken in a different way — hook and creel had subjects and no keyword, fishing line had neither.
        for (String word : new String[]{"hook", "creel", "fishing line"}) {
            Integer both = jdbc.queryForObject(
                "SELECT count(*) FROM material_process mp "
                + " WHERE EXISTS (SELECT 1 FROM unnest(string_to_array(mp.keywords, ',')) k WHERE trim(k)=?) "
                + "   AND EXISTS (SELECT 1 FROM process_subject ps WHERE ps.process_key=mp.process_key "
                + "                 AND ps.subject_term=?)", Integer.class, word, word);
            assertTrue(both != null && both > 0,
                () -> "\"" + word + "\" must be both a keyword and a subject on the same process, or it is "
                    + "inert however many of either are declared");
        }
    }

    @Test
    void noBareLineKeywordBecauseALineIsAlsoAPicketLine() {
        // The one shortening this migration refuses. Unlike hook and creel, "line" names a dozen things that are
        // not tackle — a picket line, a tether line, a line of trees — so it is held out deliberately rather
        // than by oversight, and guarded so a later cycle has to argue for it.
        Integer bare = jdbc.queryForObject(
            "SELECT count(*) FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k)='line'",
            Integer.class);
        assertTrue(bare != null && bare == 0,
            "a bare \"line\" keyword is also a picket line, a tether line and a line of trees");
    }

    @Test
    void theJavaIntentsThatOwnTheirOwnNounsAreUntouched() {
        // `trap`, `net` and `snare` are owned by SET_TRAP and CRAFT_NET and answer correctly today. A process
        // keyword cannot outrank a Java intent, so declaring one would be inert — which is why this slice does
        // not, and why that is worth asserting rather than leaving to look like an omission.
        String net = said("make a net");
        assertFalse(readsAsAMiss(net), () -> "making a net is CRAFT_NET's and must keep working: " + net);
        String snare = said("set a snare");
        assertFalse(readsAsAMiss(snare), () -> "setting a snare is SET_TRAP's: " + snare);
    }
}
