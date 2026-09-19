package com.devosphere.draugr.people;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Asking someone to travel with you, and parting (#113). No database. */
class CompanionRecognitionTest {

    @Test
    void askingAndPartingAreNamedInPlainWords() {
        assertEquals(CompanionService.Act.ASK_TO_TRAVEL_TOGETHER, CompanionService.recognise("ask them to travel with me"));
        assertEquals(CompanionService.Act.ASK_TO_TRAVEL_TOGETHER, CompanionService.recognise("Fenna, come with me"));
        assertEquals(CompanionService.Act.ASK_TO_TRAVEL_TOGETHER, CompanionService.recognise("offer companionship"));
        assertEquals(CompanionService.Act.END_COMPANIONSHIP, CompanionService.recognise("part ways"));
        assertEquals(CompanionService.Act.END_COMPANIONSHIP, CompanionService.recognise("send them home"));
    }

    @Test
    void goingSomewhereIsNotAskingAnyone() {
        for (String text : new String[]{"travel to the coast", "walk east", "go home", "come back to camp", "travel with the river"})
            assertNull(CompanionService.recognise(text), text);
    }
}
