package com.devosphere.draugr.assembly;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Naming a thing is not building it (#77).
 *
 * <p>The assembly matcher runs before the material processes, and an assembly's keywords include its bare name so
 * that "work on the drying rack" can advance one under way. Once a structure was finished, the next mention of it
 * started a NEW one — "dry the mushrooms on the drying rack", with a rack standing right there, began raising a
 * second rack and the mushrooms never dried.
 *
 * <p>The rule that fixes it is decided by the assembly's own data: a keyword that carries a verb is a request to
 * build, a keyword that is only the thing's name is a reference. These cases need no database, so they run in
 * milliseconds; the end-to-end behaviour is in {@code NamingAStationUsesItIntegrationTest}.
 */
class NamingAThingIsNotBuildingItTest {

    @Test
    void aKeywordWithAVerbIsARequestToBuild() {
        for (String keyword : new String[]{"build a drying rack", "make a drying rack", "raise a smoke rack",
                                           "lay a causeway", "dig an offal pit", "lash a hide frame",
                                           "work on the causeway", "set up a bee skep", "wall the spring head"})
            assertTrue(AssemblyService.isBuildPhrase(keyword), keyword);
    }

    @Test
    void aKeywordThatIsOnlyTheNameIsAReference() {
        for (String keyword : new String[]{"drying rack", "smoke rack", "causeway", "hide frame", "bee skep",
                                           "boardwalk", "straw hive", "fen causeway"})
            assertFalse(AssemblyService.isBuildPhrase(keyword), keyword);
    }

    @Test
    void askingForAnotherStillBuildsOne() {
        assertTrue(AssemblyService.asksForAnother("put up another drying rack"));
        assertTrue(AssemblyService.asksForAnother("a second smoke rack beside the first"));
        assertTrue(AssemblyService.asksForAnother("a new hide frame"));
        assertFalse(AssemblyService.asksForAnother("dry the mushrooms on the drying rack"));
        // Whole words only: "renew" and "newt" are not a request for a new one.
        assertFalse(AssemblyService.asksForAnother("renew the thatch"));
        assertFalse(AssemblyService.asksForAnother("catch a newt"));
    }

    /**
     * V461: with NO rack standing, "put the meat on the drying rack" matched the bare name and began raising one.
     * A process keyword that the sentence contains, longer than the name and containing it, is the speaker saying
     * more than the station's name — the assembly stands aside for it.
     */
    @Test
    void aSentenceThatSaysMoreThanTheNameIsAboutTheWork() {
        java.util.List<String> processes = java.util.List.of(
            "dry the meat,put the meat on the drying rack,lay the meat on the drying rack",
            "smoke the fish,hang the fish on the smoke rack",
            "weave a drying mat,drying rack mat");
        assertTrue(AssemblyService.saysMoreThanTheName("put the meat on the drying rack", "drying rack", processes));
        assertTrue(AssemblyService.saysMoreThanTheName("Lay the meat on the drying-rack.", "drying rack", processes));
        assertTrue(AssemblyService.saysMoreThanTheName("hang the fish on the smoke rack", "smoke rack", processes));
        assertTrue(AssemblyService.saysMoreThanTheName("weave a drying rack mat", "drying rack", processes));
        // The name alone, and work on the thing itself, still belong to the assembly.
        assertFalse(AssemblyService.saysMoreThanTheName("drying rack", "drying rack", processes));
        assertFalse(AssemblyService.saysMoreThanTheName("work on the drying rack", "drying rack", processes));
        // A process keyword that does not contain the matched name says nothing about THIS station.
        assertFalse(AssemblyService.saysMoreThanTheName("dry the meat by the smoke rack", "smoke rack", processes));
        // Whole terms: the meat put on a "drying racks" is not this keyword, and must not half-match it.
        assertFalse(AssemblyService.saysMoreThanTheName("put the meat on the drying racks", "drying rack", processes));
    }
}
