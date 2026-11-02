package com.devosphere.draugr.persistence;

import com.devosphere.draugr.routing.ActivityClassifier;
import com.devosphere.draugr.routing.ProcessMatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalogue must keep its own word (#37): a phrase a process advertises as one of its keywords has to
 * reach a process when a Chronicle says it.
 *
 * <p>This is the companion to {@code noJavaIntentShadowsAnAssemblysOwnKeywords}, which guards the same promise
 * on the assembly side. It exists because the promise was being broken wholesale and silently: the category is
 * guessed from the verb, and the verb a process is NAMED for is not always the verb its own category is keyed
 * to — "weave" is a PROCESS term while {@code weave_quiver} is CRAFT — so "weave a quiver" classified PROCESS,
 * never saw the quiver, and reached nothing. 183 declared keywords lead with a foreign-category verb, and of
 * thirty driven through a running stack, thirteen reached nothing: "build a bucket", "build a cart",
 * "build a shield", "build a travois", "cut a walking staff", "cut a whistle".
 *
 * <p><b>The invariant is narrow on purpose.</b> It only holds a keyword to account when the declaring process
 * would itself match on all three axes — category is answered by the fallback, the keyword is the phrase, and
 * one of the process's OWN subject terms appears in it. When that is true and nothing resolves, a process is
 * unreachable by a phrase it advertises, and that is a defect however it came about. A bare verb like
 * "assemble" names no material, matches no subject, and is correctly left alone.
 *
 * <p><b>Two questions, and the weaker one hid the stronger for years.</b> "Does this keyword reach anything?"
 * is not the promise a keyword makes; "does a keyword only ONE process claims reach THAT process?" is. The
 * second test here asks that, and it is the test that catches a phrase answered by a stranger — the worse
 * failure, because a wrong answer looks like a right one where a refusal does not.
 */
@SpringBootTest
class EveryDeclaredKeywordReachesAProcessIntegrationTest {

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

    @Autowired ProcessMatcher matcher;
    @Autowired ActivityClassifier classifier;

    @Test
    void everyKeywordWhoseOwnSubjectItCarriesReachesAProcess() {
        // Loaded once and resolved in memory: this walks every keyword in the catalogue, and a query per
        // phrase would make it a minute of database round trips for no extra truth.
        List<ProcessMatcher.Candidate> candidates = matcher.candidates();
        assertTrue(candidates.size() > 100, "the catalogue must be loaded for this to mean anything");

        List<String> unreachable = new ArrayList<>();
        int held = 0;
        for (ProcessMatcher.Candidate c : candidates) {
            for (String keyword : c.keywords()) {
                String phrase = keyword.trim();
                if (phrase.isEmpty()) continue;
                String normalised = ActivityClassifier.normalise(phrase);
                // Only hold the phrase to account when the process's OWN subject is in it — otherwise the
                // process could not match on all three axes anyway and its silence is correct.
                boolean carriesOwnSubject = c.subjects().stream()
                    .anyMatch(s -> ActivityClassifier.containsSubject(normalised, s));
                if (!carriesOwnSubject) continue;
                held++;

                // The play path's own rule, asked by its own name (#37). This used to reimplement the two steps
                // inline — category first, then the whole catalogue on a total miss — and a reimplemented rule
                // is a rule that can drift: when PR #832 taught the play path that a longer phrase said whole
                // beats the bare noun its verb's category had, this test went on measuring the older rule.
                // A gate that measures something other than the act cannot hold the act to account.
                String category = classifier.classify(phrase);
                ProcessMatcher.Result r = ProcessMatcher.resolveForPlay(phrase, category, candidates);
                if (r.processKey() == null) unreachable.add(c.processKey() + " advertises \"" + phrase + "\" and nothing answers it");
            }
        }

        assertTrue(held > 500,
            "this must be holding most of the catalogue to account, not a handful: only " + held + " keywords qualified");
        assertTrue(unreachable.isEmpty(),
            "these processes advertise phrases that reach nothing when a Chronicle says them, so the catalogue "
          + "is promising work it will not do (" + unreachable.size() + " of " + held + "): " + unreachable);
    }

    /**
     * THE STRONGER QUESTION, AND THE REASON THE WEAKER ONE PASSED FOR YEARS (#37).
     *
     * <p>The test above asks <i>"does this keyword reach <b>anything</b>?"</i>. That is not the promise. A
     * process declaring a keyword promises that phrase reaches <b>it</b> — and twenty-five keywords reached a
     * stranger instead, which this file called a pass every time it ran. {@code shape a wooden club} is
     * {@code make_wooden_club}'s own nineteen-letter keyword, and it was answered about timber planks by
     * {@code shape_components}, which claims the bare word "shape". A wrong answer is worse than a refusal,
     * because it looks like a right one.
     *
     * <p>So: <b>a keyword exactly ONE process claims must reach THAT process.</b> Where two or more declare
     * the same phrase, only one of them can win by design and which one is a tie question, settled by
     * {@code V318} on what is in reach — so those are left to the ambiguity rule and not asserted here.
     *
     * <p>Held against the shipped rule over the whole catalogue before this was pushed, because this is a
     * {@code @SpringBootTest} and those skip where Testcontainers cannot reach Docker. Driven from a dump of
     * the catalogue through {@code ActivityClassifier.classify} and {@code ProcessMatcher.resolveForPlay}
     * themselves, so what was measured is the shipped rule and not a copy of its reasoning:
     *
     * <pre>
     * V453   2,053 qualify   25 reached a stranger under the pre-#832 rule   0 under the rule that ships
     * V454   2,066 qualify   26 reached a stranger under the pre-#832 rule   0 under the rule that ships
     * </pre>
     *
     * <p>Both catalogue states, because either migration may be the tip when this first runs. A guard that was
     * never seen to fail proves nothing, which is why those middle numbers are recorded and not just the zeroes.
     */
    @Test
    void aKeywordOnlyOneProcessClaimsReachesThatProcess() {
        List<ProcessMatcher.Candidate> candidates = matcher.candidates();
        assertTrue(candidates.size() > 100, "the catalogue must be loaded for this to mean anything");

        // How many processes declare each phrase. Normalised, because that is how the matcher reads it:
        // "ox-yoke" and "ox yoke" are one claim on one phrase, not two processes sharing it.
        Map<String, Set<String>> claimedBy = new HashMap<>();
        for (ProcessMatcher.Candidate c : candidates)
            for (String keyword : c.keywords()) {
                String phrase = keyword.trim();
                if (!phrase.isEmpty())
                    claimedBy.computeIfAbsent(ActivityClassifier.normalise(phrase), k -> new HashSet<>())
                             .add(c.processKey());
            }

        List<String> stolen = new ArrayList<>();
        int held = 0;
        for (ProcessMatcher.Candidate c : candidates) {
            for (String keyword : c.keywords()) {
                String phrase = keyword.trim();
                if (phrase.isEmpty()) continue;
                String normalised = ActivityClassifier.normalise(phrase);
                // Shared phrases are a tie question, not a theft: skip anything a second process also claims.
                if (claimedBy.getOrDefault(normalised, Set.of()).size() != 1) continue;
                // Same narrowing as the test above: a phrase carrying none of its own process's subject terms
                // could not match on all three axes anyway, so its silence is correct and not this test's business.
                boolean carriesOwnSubject = c.subjects().stream()
                    .anyMatch(s -> ActivityClassifier.containsSubject(normalised, s));
                if (!carriesOwnSubject) continue;
                held++;

                ProcessMatcher.Result r =
                    ProcessMatcher.resolveForPlay(phrase, classifier.classify(phrase), candidates);
                if (r.processKey() != null && !r.processKey().equals(c.processKey()))
                    stolen.add("\"" + phrase + "\" is claimed only by " + c.processKey()
                             + " and reached " + r.processKey());
            }
        }

        assertTrue(held > 1500,
            "this must be holding most of the catalogue to account, not a handful: only " + held + " keywords qualified");
        assertTrue(stolen.isEmpty(),
            "these phrases are advertised by exactly one process and answered by a different one, so a Chronicle "
          + "saying a recipe's own name is told about something else (" + stolen.size() + " of " + held + "): " + stolen);
    }
}
