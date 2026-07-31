package com.devosphere.draugr.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The routing vocabulary and process definitions as V54 seeds them, mirrored here so
 * the resolution rule can be tested without a database — the convention every other
 * test in this project follows.
 *
 * <p>This is a copy, and copies drift. The drift is guarded from the other side:
 * {@code PersistentStateAuditor} checks the same invariants against the live schema,
 * so a migration that changes categories or subjects without updating this file shows
 * up as an audit violation rather than as a test that quietly still passes.
 *
 * <p>Source: {@code V54__activity_categories.sql}, plus the subject terms it derives
 * from each process's own inputs and outputs, read back from a migrated database.
 */
final class RoutingFixture {

    private RoutingFixture() { }

    /** activity_category.precedence — lower wins a tie. */
    static final Map<String, Integer> PRECEDENCE = Map.of(
        "HUNT", 1, "ACQUIRE", 2, "PROCESS", 3, "CRAFT", 4, "CONSTRUCT", 5,
        "MAINTAIN", 6, "INHABIT", 7, "RECORD", 8, "OBSERVE", 9, "MOVE", 10);

    static final List<ActivityClassifier.Term> VOCABULARY = vocabulary();

    private static List<ActivityClassifier.Term> vocabulary() {
        List<ActivityClassifier.Term> t = new ArrayList<>();
        add(t, "ACQUIRE", 3, "forage", "quarry", "mine", "prospect", "strip bark", "fell", "draw water");
        add(t, "ACQUIRE", 2, "gather", "collect", "harvest", "dig", "ore", "scavenge");
        add(t, "ACQUIRE", 1, "pick", "fetch", "take");

        add(t, "HUNT", 3, "hunt", "stalk", "snare", "fish", "butcher", "bait", "slaughter");
        add(t, "HUNT", 2, "ambush", "trap", "spear", "shoot", "skin", "lure", "track", "kill", "gut");
        add(t, "HUNT", 1, "angle");

        add(t, "PROCESS", 3, "tan", "ret", "knap", "leach", "hew", "brine", "fillet", "debark",
                             "foundation stone", "bake", "kiln");
        add(t, "PROCESS", 2, "split", "shape", "render", "twist", "smoke", "dry", "cure",
                             "grind", "soak", "boil", "temper", "flesh", "scrape", "plane", "saw",
                             "salt", "process", "refine", "extract", "strip", "pound", "crush", "sift",
                             "harden");
        add(t, "PROCESS", 1, "dress", "season", "cut", "weave");

        add(t, "CRAFT", 3, "craft", "sew", "stitch", "haft", "fletch", "nock");
        add(t, "CRAFT", 2, "assemble", "carve", "fashion", "string", "lash", "glue",
                           "form", "mold", "mould", "shape into", "weave into", "basket");
        add(t, "CRAFT", 1, "make", "fit", "bind", "attach", "join");

        add(t, "CONSTRUCT", 3, "construct", "erect", "roof", "thatch", "foundation",
                               "weatherproof", "joinery", "rafter", "pitch the roof");
        add(t, "CONSTRUCT", 2, "raise", "frame", "wall", "floor", "flashing", "ridge",
                               "beam", "sill", "stud", "build", "lash", "structural");
        add(t, "CONSTRUCT", 1, "post", "lay", "fix");

        add(t, "MAINTAIN", 3, "repair", "mend", "sharpen", "maintain");
        add(t, "MAINTAIN", 2, "patch", "reinforce", "replace");
        add(t, "MAINTAIN", 1, "oil");

        add(t, "INHABIT", 3, "sleep", "eat", "drink", "cook", "tend the fire", "add fuel", "light a fire");
        add(t, "INHABIT", 2, "rest", "warm", "store");
        add(t, "INHABIT", 1, "shelter");

        add(t, "RECORD", 3, "write", "sketch", "inscribe");
        add(t, "RECORD", 2, "map", "record", "document", "archive", "draw a");
        add(t, "RECORD", 1, "note");

        add(t, "OBSERVE", 3, "observe", "inspect", "examine", "survey");
        add(t, "OBSERVE", 2, "look", "watch", "listen", "smell", "study", "scan");

        add(t, "MOVE", 3, "travel");
        add(t, "MOVE", 2, "walk", "climb", "swim", "cross", "journey");
        add(t, "MOVE", 1, "head", "return", "follow");
        return List.copyOf(t);
    }

    private static void add(List<ActivityClassifier.Term> into, String category, int weight, String... terms) {
        for (String term : terms) into.add(new ActivityClassifier.Term(category, term, weight));
    }

    /**
     * The twenty VERIFIED processes: key, category, keywords, and derived subject
     * terms, exactly as a migrated database reports them.
     */
    static final List<ProcessMatcher.Candidate> PROCESSES = List.of(
        ProcessMatcher.Candidate.of("carve_needle", "CRAFT",
            "needle,awl,bone needle,carve bone,carve,whittle",
            "animal,antler,bone,fish,needle"),
        ProcessMatcher.Candidate.of("cut_leather_cord", "PROCESS",
            "leather cord,thong,lace,cut leather,cut,slice",
            "cord,leather,tanned,thong"),
        ProcessMatcher.Candidate.of("dress_construction", "PROCESS",
            "construction stone,break the stone,dress rubble,coursing stone,dress,break,course",
            "construction,field,medium,rock,stone"),
        ProcessMatcher.Candidate.of("dress_foundation", "PROCESS",
            "foundation stone,dress stone,square the stone,block,dress,square",
            "field,foundation,heavy,rock,stone"),
        ProcessMatcher.Candidate.of("fire_vessel", "PROCESS",
            "fire the pot,fire the clay,bake the pot,bake the clay,fire the vessel,fire pottery,harden the pot,kiln,fire,bake,harden",
            "clay,pot,unfired,vessel"),
        ProcessMatcher.Candidate.of("form_vessel", "CRAFT",
            "form pot,shape clay,coil pot,mould,mold,make a vessel,shape a pot,form,coil,shape,make",
            "clay,lump,pot,unfired,vessel"),
        ProcessMatcher.Candidate.of("gather_ash", "ACQUIRE",
            "ash,gather ash,scoop ash,rake the ashes,gather,scoop,rake,collect",
            "ash,fire,hearth,wood"),
        ProcessMatcher.Candidate.of("haft_stone_axe", "CRAFT",
            "stone axe,felling axe,haft an axe,make an axe,haft,fit,mount,make",
            "axe,component,cordage,fiber,handle,head,precision,processed,stone,tool,wooden"),
        ProcessMatcher.Candidate.of("knap_tool_stone", "PROCESS",
            "knap,tool stone,flake,strike flakes,work the flint,knap,strike,flake",
            "core,field,flint,precision,rock,stone,tool"),
        ProcessMatcher.Candidate.of("leach_lye", "PROCESS",
            "lye,leach,leaching,ash water,leach",
            "alkali,ash,lye,solution,wood"),
        ProcessMatcher.Candidate.of("reinforce_timber", "CONSTRUCT",
            "reinforce,structural,beam,lash the planks,bind the planks,lash,bind,reinforce",
            "beam,cordage,fiber,plank,processed,reinforced,structural,timber,wood"),
        ProcessMatcher.Candidate.of("render_pitch", "PROCESS",
            "pitch,tar,render resin,melt resin,glue,render,melt,boil,process",
            "charcoal,pine,pitch,resin,sap,tar"),
        ProcessMatcher.Candidate.of("ret_nettle", "PROCESS",
            "ret,soak nettle,nettle cordage,strip nettle,ret,soak,strip",
            "cordage,fiber,nettle,processed,stalk"),
        ProcessMatcher.Candidate.of("shape_components", "PROCESS",
            "shape,carve component,whittle,dress the plank,trim the plank,carve,trim,shape,plane,taper",
            "billet,component,plank,processed,stave,timber,wood,wooden"),
        ProcessMatcher.Candidate.of("split_planks", "PROCESS",
            "split,plank,saw the log,cut the log,hew,rive,split,rive,saw,cut",
            "ash,birch,board,log,lumber,maple,oak,pine,plank,spruce,timber,wood"),
        ProcessMatcher.Candidate.of("tan_hide", "PROCESS",
            "tan,tanning,bark tan,cure the hide,soak the hide,tan,cure,soak",
            "animal,bark,boar,deer,hide,leather,oak,pelt,skin,tanned"),
        ProcessMatcher.Candidate.of("timber_from_log", "PROCESS",
            "square the log,baulk,timber baulk,dress the log,square,hew,dress",
            "ash,birch,log,maple,oak,pine,spruce,timber,tree,trunk,wood"),
        ProcessMatcher.Candidate.of("twist_cordage", "PROCESS",
            "cordage,twist,twine,rope,cord,spin fiber,twist,spin,ply",
            "bundle,cordage,fiber,plant,processed,rope,string,twine"),
        ProcessMatcher.Candidate.of("weave_large_basket", "CRAFT",
            "large basket,big basket,pannier,carrying basket,weave,plait",
            "basket,bundle,carry,fiber,hand,plant,vine,withy"),
        ProcessMatcher.Candidate.of("weave_textile", "PROCESS",
            "textile,weave cloth,weaving,cloth,fabric,weave",
            "cloth,cordage,fabric,fiber,material,processed,processing,textile"));

    /** Classify then match, the way the runtime does. */
    static String resolve(String text) {
        String category = ActivityClassifier.classify(text, VOCABULARY, PRECEDENCE);
        return ProcessMatcher.match(text, category, PROCESSES);
    }
}
