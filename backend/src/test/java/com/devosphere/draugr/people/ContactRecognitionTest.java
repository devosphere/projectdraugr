package com.devosphere.draugr.people;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which act of contact a Chronicle's words name (#112). No database: recognition is a pure function of the text, and
 * whether it takes effect at all is decided elsewhere by whether a people is within sight.
 */
class ContactRecognitionTest {

    @Test
    void theTicketsActsAreEachReachableInPlainWords() {
        assertEquals(ContactService.Act.OBSERVE_SETTLEMENT, ContactService.recognise("I watch the village from the reeds"));
        assertEquals(ContactService.Act.LISTEN_FOR_LANGUAGE, ContactService.recognise("listen to their speech"));
        assertEquals(ContactService.Act.ANNOUNCE_PRESENCE, ContactService.recognise("call out to them across the water"));
        assertEquals(ContactService.Act.APPROACH_BOUNDARY, ContactService.recognise("approach the edge of the isle"));
        assertEquals(ContactService.Act.DISPLAY_EMPTY_HANDS, ContactService.recognise("show them my empty hands"));
        assertEquals(ContactService.Act.LOWER_WEAPON, ContactService.recognise("lower my spear"));
        assertEquals(ContactService.Act.OFFER_GIFT, ContactService.recognise("offer them the dried fish"));
        assertEquals(ContactService.Act.LEAVE_GIFT, ContactService.recognise("leave a gift at the edge"));
        assertEquals(ContactService.Act.ASK_PERMISSION, ContactService.recognise("ask permission to come up"));
        assertEquals(ContactService.Act.MAKE_PROMISE, ContactService.recognise("give them my word I will not fish past the markers"));
        assertEquals(ContactService.Act.WITHDRAW, ContactService.recognise("take my leave"));
    }

    @Test
    void theLongestPhraseDecides() {
        assertEquals(ContactService.Act.SHOW_MAP, ContactService.recognise("show them my map"),
            "showing a map is its own act, not the showing of any item");
        assertEquals(ContactService.Act.SHOW_ITEM, ContactService.recognise("show them the flint knife"));
    }

    @Test
    void ordinaryWorkIsNotContact() {
        for (String text : new String[]{"gather reeds", "fish the channel", "walk north", "eat the dried fish", "build a hide frame"})
            assertNull(ContactService.recognise(text), text);
    }

    @Test
    void speechNeedsMoreUnderstandingThanShowingAThing() {
        assertEquals(0, ContactService.understandingNeeded(ContactService.Act.SHOW_ITEM), "a thing held up is understood by sight");
        assertTrue(ContactService.understandingNeeded(ContactService.Act.MAKE_PROMISE)
                 > ContactService.understandingNeeded(ContactService.Act.ATTEMPT_GESTURE),
            "a promise asks far more of shared understanding than a gesture does");
    }
}
