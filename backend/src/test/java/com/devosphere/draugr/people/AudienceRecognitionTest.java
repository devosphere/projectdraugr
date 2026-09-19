package com.devosphere.draugr.people;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Asking who speaks for a people, who does what, and standing with them in mourning (#121). No database. */
class AudienceRecognitionTest {

    @Test
    void theOfficeIsAskedAboutInPlainWords() {
        assertEquals(AudienceService.Act.ASK_FOR_SPEAKER, AudienceService.recognise("ask who speaks for them"));
        assertEquals(AudienceService.Act.ASK_FOR_SPEAKER, AudienceService.recognise("request an audience with their chief"));
        assertEquals(AudienceService.Act.ASK_ABOUT_ROLES, AudienceService.recognise("ask who does what"));
        assertEquals(AudienceService.Act.OFFER_CONDOLENCE, AudienceService.recognise("offer my condolences"));
        assertEquals(AudienceService.Act.OFFER_CONDOLENCE, AudienceService.recognise("pay my respects at the grave"));
    }

    @Test
    void otherWordsAreNotAnAudience() {
        for (String text : new String[]{"trade with them", "ask their permission", "apologise to them", "work for them", "look around"})
            assertNull(AudienceService.recognise(text), text);
    }
}
