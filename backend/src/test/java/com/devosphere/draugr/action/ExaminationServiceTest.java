package com.devosphere.draugr.action;

import com.devosphere.draugr.capability.CapabilityAdaptationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #25: examination reveals a subject at a DEPTH set by the relevant mastery. Here the subject is the
 * place (no item named), so ANALYZE leans on insight — a novice reads only the surface, a practiced
 * hand reads deeper. Pure over its two collaborators (jdbc + capability), so no context is needed.
 */
class ExaminationServiceTest {

    private static final UUID CH = UUID.randomUUID();
    private static final UUID LOC = UUID.randomUUID();
    // A fragment that only the tier-2 layer of a grassland ANALYZE adds.
    private static final String DEEPER = "open sight-lines";

    private ExaminationService withInsight(double insight) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CapabilityAdaptationService cap = mock(CapabilityAdaptationService.class);
        // No item is reachable, so the place itself is the subject.
        when(jdbc.queryForList(anyString(), any(UUID.class))).thenReturn(Collections.emptyList());
        doReturn("GRASSLAND").when(jdbc).query(anyString(), any(ResultSetExtractor.class), any(UUID.class));
        when(cap.familiarity(CH, "ATTENTION")).thenReturn(0.0);
        when(cap.familiarity(CH, "INSIGHT")).thenReturn(insight);
        when(cap.familiarity(CH, "KNOWLEDGE")).thenReturn(0.0);
        return new ExaminationService(jdbc, cap);
    }

    @Test void noviceReadsOnlyTheSurface() {
        String prose = withInsight(0.0).examine(CH, LOC, "analyze this ground", ExaminationService.Mode.ANALYZE)[1];
        assertFalse(prose.contains(DEEPER), "a novice's insight (0) reveals tier 1 only: " + prose);
        assertTrue(prose.contains("what it offers"), "the surface reading is always present");
    }

    @Test void practisedHandReadsDeeper() {
        String prose = withInsight(0.03).examine(CH, LOC, "analyze this ground", ExaminationService.Mode.ANALYZE)[1];
        assertTrue(prose.contains(DEEPER), "insight past the tier-2 threshold reveals the deeper layer: " + prose);
    }

    /**
     * THE WORD A PLAYER USES FOR THE KIND OF A THING is the LAST noun of the sentence (#37).
     *
     * <p>Only the final noun is read, and that is the whole guard against collisions: the catalogue holds
     * exactly three ({@code grass} inside {@code grass_snake}, {@code tree} inside {@code tree_frog} and
     * {@code tree_newt}), found by asking it before the rule was written. Scanning the sentence for any head
     * noun would have answered {@code examine the grass snake} about a grass.
     */
    @Test void theWordForAKindIsTheLastNounSaid() {
        assertEquals("mushroom", ExaminationService.headNounSaid("examine the mushroom"));
        assertEquals("cap", ExaminationService.headNounSaid("examine the death cap"));
        assertEquals("onion", ExaminationService.headNounSaid("inspect the wild onion"));

        // THE THREE COLLISIONS, each resolving to the word the sentence actually ends on.
        assertEquals("snake", ExaminationService.headNounSaid("examine the grass snake"),
            "not \"grass\" — a sentence about a snake must never be answered about a plant");
        assertEquals("frog", ExaminationService.headNounSaid("examine the tree frog"));
        assertEquals("newt", ExaminationService.headNounSaid("examine the tree newt"));

        // A TRAILING WORD THAT IS NOT THE THING.
        assertEquals("mushroom", ExaminationService.headNounSaid("examine the mushroom closely"));
        assertEquals("mushroom", ExaminationService.headNounSaid("look the mushroom over"));
        assertEquals("fungus", ExaminationService.headNounSaid("examine the fungus here"));

        // AND PUNCTUATION AND CASE ARE NOT PART OF THE WORD.
        assertEquals("mushroom", ExaminationService.headNounSaid("examine the mushroom."));
        assertEquals("", ExaminationService.headNounSaid(""));
        assertEquals("", ExaminationService.headNounSaid(null));
    }
}
