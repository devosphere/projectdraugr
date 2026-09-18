package com.devosphere.draugr.people;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the season gives a native community's workers (#111). No database: the rule is a function of the date, and
 * the thresholds that turn hunger into closing up and moving on are checked for the order they must come in.
 */
class TheSeasonFeedsAPeopleTest {

    @Test
    void theSeasonDecidesWhatAWorkerBringsIn() {
        assertEquals(0, NativeCommunityService.yieldPerWorker(Instant.parse("2031-01-15T12:00:00Z")), "deep winter gives nothing");
        assertEquals(1, NativeCommunityService.yieldPerWorker(Instant.parse("2031-03-15T12:00:00Z")), "the shoulder of the year a little");
        assertEquals(2, NativeCommunityService.yieldPerWorker(Instant.parse("2031-07-15T12:00:00Z")), "summer enough to share");
    }

    @Test
    void aCommunityClosesUpBeforeItLeaves() {
        assertTrue(NativeCommunityService.SHORTAGE_CLOSES < NativeCommunityService.SHORTAGE_MOVES,
            "a hungry people shuts its store to outsiders long before it abandons its home");
        assertTrue(NativeCommunityService.MAX_DAYS_PER_STEP >= NativeCommunityService.SHORTAGE_MOVES,
            "one catch-up step must be able to carry a community all the way from its first hungry day to leaving");
    }
}
