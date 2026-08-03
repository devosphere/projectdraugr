package com.devosphere.draugr.action;

import com.devosphere.draugr.capability.CapabilityAdaptationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The examination verbs (GitHub #25): INSPECT, ANALYZE, INVESTIGATE. Each reveals a subject — a specific
 * object the chronicle can reach, or the place itself — at a DEPTH set by the relevant mastery: perception
 * (attention), insight (understanding), knowledge (inference). A novice sees the surface; a practiced hand
 * reads deeper. Witness-stance: it states what is perceived and reasoned, never advises. Deterministic;
 * the AI layer can later enrich the deepest tier with open-ended detail, but the facts here are the world's.
 *
 * <p>The three verbs lean on different masteries, which is also which one each grows (mapped in
 * ChronicleActionService): INSPECT → perception, ANALYZE → insight, INVESTIGATE → knowledge.
 */
@Service
public class ExaminationService {

    public enum Mode { INSPECT, ANALYZE, INVESTIGATE }

    private final JdbcTemplate jdbc;
    private final CapabilityAdaptationService capability;

    public ExaminationService(JdbcTemplate jdbc, CapabilityAdaptationService capability) {
        this.jdbc = jdbc;
        this.capability = capability;
    }

    /** Depth tier 1..3 from a mastery level (0..1). Deliberately reachable but earned. */
    private static int tier(double mastery) { return mastery >= 0.05 ? 3 : mastery >= 0.01 ? 2 : 1; }

    @Transactional(readOnly = true)
    public String[] examine(UUID chronicle, UUID location, String actionText, Mode mode) {
        String lower = actionText == null ? "" : actionText.toLowerCase(Locale.ROOT);
        double perception = capability.familiarity(chronicle, "ATTENTION");
        double insight = capability.familiarity(chronicle, "INSIGHT");
        double knowledge = capability.familiarity(chronicle, "KNOWLEDGE");

        Map<String, Object> item = resolveItem(chronicle, lower);
        if (item != null) {
            String out = switch (mode) {
                case INSPECT     -> inspectItem(item, tier(perception + insight));
                case ANALYZE     -> analyzeItem(item, tier(insight));
                case INVESTIGATE -> investigateItem(item, tier(perception + insight + knowledge));
            };
            return new String[]{"SUCCEEDED", out};
        }
        String biome = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : "unknown", location);
        String out = switch (mode) {
            case INSPECT     -> inspectPlace(biome, tier(perception + insight));
            case ANALYZE     -> analyzePlace(biome, tier(insight));
            case INVESTIGATE -> investigatePlace(biome, tier(perception + insight + knowledge));
        };
        return new String[]{"SUCCEEDED", out};
    }

    /** A reachable item the text names, or null — then the place is the subject. */
    private Map<String, Object> resolveItem(UUID chronicle, String lower) {
        List<Map<String, Object>> items = jdbc.queryForList(
            "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' " +
            "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') " +
            "SELECT DISTINCT i.item_key, d.display_name, d.category, d.unit_mass_grams, i.quality_grade, i.condition_state, i.object_id " +
            "FROM reachable r JOIN item_instance i ON i.object_id=r.id JOIN item_definition d ON d.item_key=i.item_key ORDER BY d.display_name", chronicle);
        for (Map<String, Object> it : items) {
            String key = ((String) it.get("item_key")).replace('_', ' ');
            String name = ((String) it.get("display_name")).toLowerCase(Locale.ROOT);
            if (lower.contains(key) || lower.contains(name)) return it;
        }
        return null;
    }

    // ---- item ----------------------------------------------------------------------------------------

    private String inspectItem(Map<String, Object> it, int tier) {
        String name = (String) it.get("display_name");
        String cond = condition((String) it.get("condition_state"));
        StringBuilder b = new StringBuilder("You take up the ").append(name.toLowerCase(Locale.ROOT)).append(" and look it over. ").append(cond);
        if (tier >= 2) b.append(" ").append(heftLine(number(it.get("unit_mass_grams"))));
        if (tier >= 3) b.append(" ").append(gradeLine((String) it.get("quality_grade")));
        return b.toString();
    }

    private String analyzeItem(Map<String, Object> it, int tier) {
        String name = ((String) it.get("display_name")).toLowerCase(Locale.ROOT);
        String cat = String.valueOf(it.get("category"));
        StringBuilder b = new StringBuilder("You study the ").append(name).append(" closely. ").append(natureOf(cat));
        if (tier >= 2) b.append(" ").append(useOf(cat));
        if (tier >= 3) b.append(" ").append(gradeLine((String) it.get("quality_grade")));
        return b.toString();
    }

    private String investigateItem(Map<String, Object> it, int tier) {
        String name = ((String) it.get("display_name")).toLowerCase(Locale.ROOT);
        String origin = jdbc.query(
            "SELECT transition_type FROM object_transition WHERE object_id=? ORDER BY occurred_at LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, it.get("object_id"));
        StringBuilder b = new StringBuilder("You turn the ").append(name).append(" over, reading it for its history. ").append(originLine(origin));
        if (tier >= 2) b.append(" ").append(gradeLine((String) it.get("quality_grade")));
        if (tier >= 3) b.append(" You are as sure of this as of anything you have not seen with your own eyes.");
        return b.toString();
    }

    // ---- place ---------------------------------------------------------------------------------------

    private String inspectPlace(String biome, int tier) {
        StringBuilder b = new StringBuilder("You take a slow look at the ground and what stands on it. ").append(biomeSurface(biome));
        if (tier >= 2) b.append(" ").append(biomeSigns(biome));
        return b.toString();
    }

    private String analyzePlace(String biome, int tier) {
        StringBuilder b = new StringBuilder("You read the country for what it offers. ").append(biomeOffers(biome));
        if (tier >= 2) b.append(" ").append(biomeSigns(biome));
        return b.toString();
    }

    private String investigatePlace(String biome, int tier) {
        StringBuilder b = new StringBuilder("You piece together what this ground has been through. ").append(biomeHistory(biome));
        if (tier >= 2) b.append(" ").append(biomeOffers(biome));
        return b.toString();
    }

    // ---- fact fragments ------------------------------------------------------------------------------

    private static String condition(String c) {
        return switch (c == null ? "" : c) {
            case "SOUND" -> "It is whole and sound.";
            case "WORN" -> "It shows honest wear, but holds.";
            case "DAMAGED" -> "It is damaged — a crack, a looseness, something given way.";
            default -> "It is what it is.";
        };
    }
    private String heftLine(int grams) {
        return grams >= 1500 ? "It has real weight to it — a two-handed thing." : grams >= 400 ? "It sits with a solid, working heft." : "It is light in the hand.";
    }
    private static String gradeLine(String grade) {
        return switch (grade == null ? "" : grade) {
            case "FINE" -> "The workmanship is fine — clean lines, no wasted effort.";
            case "SOUND" -> "The workmanship is sound and honest.";
            case "POOR" -> "The workmanship is poor; it will do, but only just.";
            case "DEFECTIVE" -> "There is a real flaw in the making of it.";
            default -> "You cannot read much into how it was made.";
        };
    }
    private static String natureOf(String cat) {
        return switch (cat == null ? "" : cat) {
            case "FOOD" -> "It is food — something the body can take.";
            case "MATERIAL" -> "It is raw stock, meant to be worked into something else.";
            case "TOOL" -> "It is a tool, shaped for a task.";
            case "EQUIPMENT" -> "It is gear, made to be worn or carried into use.";
            case "CONTAINER" -> "It is made to hold other things.";
            default -> "It is a made or found thing, plain enough.";
        };
    }
    private static String useOf(String cat) {
        return switch (cat == null ? "" : cat) {
            case "FOOD" -> "Eaten, it answers hunger; left too long, it turns.";
            case "MATERIAL" -> "Its worth is in what it can become, not what it is.";
            case "TOOL" -> "In the right hands it does the work of many bare ones.";
            case "EQUIPMENT" -> "Worn well, it changes what the body can bear or do.";
            default -> "Its use is whatever a working hand can make of it.";
        };
    }
    private static String originLine(String transition) {
        return switch (transition == null ? "" : transition) {
            case "GATHERED" -> "It came from the land — gathered, not made.";
            case "CRAFTED" -> "It was crafted by hand; the shaping is deliberate.";
            case "PROCESSED" -> "It was worked from something rawer than itself.";
            default -> "Its beginning is not written plainly on it.";
        };
    }
    private static String biomeSurface(String biome) {
        return switch (biome == null ? "" : biome) {
            case "TEMPERATE_FOREST" -> "Leaf litter, deadfall, and close trunks — the floor holds more than it first shows.";
            case "MOUNTAIN" -> "Bare rock and scree, with little soil and less cover.";
            case "HIGHLAND" -> "Thin turf over hard ground, sloping and open.";
            case "GRASSLAND" -> "Grass to the horizon, and the ground firm beneath it.";
            case "WETLAND" -> "Soft, wet ground that gives underfoot, water standing in the low places.";
            default -> "Plain ground, unremarkable at a glance.";
        };
    }
    private static String biomeSigns(String biome) {
        return switch (biome == null ? "" : biome) {
            case "TEMPERATE_FOREST" -> "Where the light breaks through, there is growth worth knowing.";
            case "MOUNTAIN" -> "The stone itself is the resource here, if you can work it.";
            case "WETLAND" -> "Reeds, clay, and standing water each have their uses to a patient hand.";
            case "GRASSLAND" -> "Fibrous growth and open sight-lines — good for some things, exposed for others.";
            case "HIGHLAND" -> "Hardy growth clings where it can, and the wind decides much.";
            default -> "There is little here that declares itself.";
        };
    }
    private static String biomeOffers(String biome) {
        return switch (biome == null ? "" : biome) {
            case "TEMPERATE_FOREST" -> "Wood, fibre, forage, and cover — a forest keeps a person alive if they read it.";
            case "MOUNTAIN" -> "Stone and mineral for those with the tools and the patience to take them.";
            case "WETLAND" -> "Water, clay, reeds, and low forage — rich, if you can bear the wet.";
            case "GRASSLAND" -> "Fibre and open ground; less to build with, more to see coming.";
            case "HIGHLAND" -> "Sparse forage and hard stone, and a long view in every direction.";
            default -> "What it offers is not obvious, and may be little.";
        };
    }
    private static String biomeHistory(String biome) {
        return switch (biome == null ? "" : biome) {
            case "TEMPERATE_FOREST" -> "The stand is old — deadfall on deadfall, ground built up over long seasons.";
            case "MOUNTAIN" -> "This rock was shaped by weather over spans no life measures.";
            case "WETLAND" -> "Water has stood and moved here a long time; the ground remembers it.";
            case "GRASSLAND" -> "Fire and grazing keep this open — it was not always so, and will not always be.";
            case "HIGHLAND" -> "Wind and cold have worn this high ground down to what endures.";
            default -> "The ground gives up little of where it has been.";
        };
    }
    private static int number(Object o) { return o instanceof Number n ? n.intValue() : 0; }
}
