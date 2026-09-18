package com.devosphere.draugr.people;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an exchange says and what things are worth to a people who make their own mats (#113). No database. */
class TradeTermsTest {

    @Test
    void anOfferIsAnExchangeBecauseOfTheFor() {
        assertEquals(TradeService.Act.MAKE_OFFER, TradeService.recognise("offer them my flint knife for two reed mats"));
        assertEquals(TradeService.Act.MAKE_OFFER, TradeService.recognise("trade my cordage for a fish trap"));
        assertEquals(TradeService.Act.OPEN_TRADE, TradeService.recognise("ask to trade"));
        assertEquals(TradeService.Act.ACCEPT_TRADE, TradeService.recognise("accept their offer"));
        assertEquals(TradeService.Act.DECLINE_TRADE, TradeService.recognise("decline their offer"));
        assertNull(TradeService.recognise("offer them the dried fish"), "without an exchange it is a gift, which is contact (#112)");
        assertNull(TradeService.recognise("walk to the river"));
    }

    @Test
    void theNumberAskedForIsReadFromTheWords() {
        assertEquals(2, TradeService.count(" two reed mats"));
        assertEquals(3, TradeService.count("three reed mats"));
        assertEquals(2, TradeService.count("a couple of baskets"));
        assertEquals(1, TradeService.count("a fish trap"));
        assertEquals(4, TradeService.count("4 mats"));
    }

    @Test
    void anEdgeTheyCannotMakeIsWorthMoreThanAnotherOfWhatTheyDo() {
        int knife = TradeService.worth("flint_knife", "TOOL", false, false, "dried_fish");
        int mat = TradeService.worth("reed_mat", "MATERIAL", true, false, "dried_fish");
        assertTrue(knife >= 2 * mat, "a blade from outside buys two mats");
        assertTrue(TradeService.worth("dried_fish", "FOOD", false, true, "dried_fish") > TradeService.worth("dried_fish", "FOOD", false, false, "dried_fish"),
            "food is worth more to a hungry isle");
    }
}
