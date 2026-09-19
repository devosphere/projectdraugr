package com.devosphere.draugr.people;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which act of paid work a Chronicle's words name (#113). No database. */
class AgreementRecognitionTest {

    @Test
    void anOfferNamesAWageAndWorkingDoesNot() {
        assertEquals(AgreementService.Act.OFFER_WORK, AgreementService.recognise("offer to work for them for two dried fish"));
        assertEquals(AgreementService.Act.OFFER_WORK, AgreementService.recognise("work for them for a fish trap"));
        assertEquals(AgreementService.Act.OFFER_WORK, AgreementService.recognise("offer my labour for three reed mats"));
        assertEquals(AgreementService.Act.DO_WORK, AgreementService.recognise("work for them"));
        assertEquals(AgreementService.Act.DO_WORK, AgreementService.recognise("do a day's work"));
        assertEquals(AgreementService.Act.DO_WORK, AgreementService.recognise("work for them for a day"), "a length of time is not a wage");
        assertEquals(AgreementService.Act.ASK_RELEASE, AgreementService.recognise("ask to be released from the work"));
        assertEquals(AgreementService.Act.BREAK_AGREEMENT, AgreementService.recognise("abandon the work"));
        assertEquals(AgreementService.Act.ASK_TERMS, AgreementService.recognise("what do i owe them"));
    }

    @Test
    void ordinaryWorkIsNotAnAgreement() {
        for (String text : new String[]{"work the hide for a cloak", "work on the lean-to", "fish the channel", "trade my knife for two mats",
                                        "help them fish", "offer them my knife for a fish trap"})
            assertNull(AgreementService.recognise(text), text);
    }
}
