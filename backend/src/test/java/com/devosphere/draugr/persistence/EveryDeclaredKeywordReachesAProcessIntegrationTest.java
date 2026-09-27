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
import java.util.List;

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

                // The same two steps the play path takes: the guessed category first, then the rest of the
                // catalogue when that category has no answer.
                String category = classifier.classify(phrase);
                ProcessMatcher.Result r = ProcessMatcher.resolve(phrase, category, candidates);
                if (r.processKey() == null && category != null) r = ProcessMatcher.resolve(phrase, null, candidates);
                if (r.processKey() == null) unreachable.add(c.processKey() + " advertises \"" + phrase + "\" and nothing answers it");
            }
        }

        assertTrue(held > 500,
            "this must be holding most of the catalogue to account, not a handful: only " + held + " keywords qualified");
        assertTrue(unreachable.isEmpty(),
            "these processes advertise phrases that reach nothing when a Chronicle says them, so the catalogue "
          + "is promising work it will not do (" + unreachable.size() + " of " + held + "): " + unreachable);
    }
}
