package com.devosphere.draugr.people;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Asking for a place among a people, drawing a share, and giving the place up (#113). No database. */
class MembershipRecognitionTest {

    @Test
    void aPlaceIsAskedForInPlainWords() {
        assertEquals(MembershipService.Act.ASK_TO_JOIN, MembershipService.recognise("ask to join them"));
        assertEquals(MembershipService.Act.ASK_TO_JOIN, MembershipService.recognise("ask for a place among them"));
        assertEquals(MembershipService.Act.TAKE_SHARE, MembershipService.recognise("take my share"));
        assertEquals(MembershipService.Act.LEAVE, MembershipService.recognise("give up my place"));
        assertEquals(MembershipService.Act.LEAVE, MembershipService.recognise("ask to leave the settlement"));
    }

    @Test
    void otherWordsAreNotAPlace() {
        for (String text : new String[]{"steal from their store", "trade with them", "work for them", "leave the isle", "eat the dried fish"})
            assertNull(MembershipService.recognise(text), text);
    }
}
