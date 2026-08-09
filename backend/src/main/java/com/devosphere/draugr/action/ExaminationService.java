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
        // Deliberate looking reveals what is actually alive here (#33/#37), scaled by mastery: a base acuity
        // for looking on purpose, plus the perception/insight/knowledge the mode leans on.
        double acuity = switch (mode) {
            case INSPECT     -> 0.6 + perception + insight;
            case ANALYZE     -> 0.6 + perception + insight;
            case INVESTIGATE -> 0.6 + perception + insight + knowledge;
        };
        String life = presentLife(location, Math.min(1.0, acuity));
        return new String[]{"SUCCEEDED", life.isEmpty() ? out : out + " " + life};
    }

    /**
     * Witness-stance naming of the life actually present in a chunk — flora, wildlife, fish, insects — scaled
     * by {@code acuity} (0..1, from attention + perception mastery). The world stored these rows all along
     * (#37/#33), but perception never named them, so a Chronicle could not know what was here to hunt, gather,
     * or avoid. A keener eye sees more kinds and reads the shy ones out of their sign. Deterministic and
     * read-only; the AI narrator may later enrich the deepest tier without becoming load-bearing.
     */
    @Transactional(readOnly = true)
    public String presentLife(UUID chunk, double acuity) {
        if (chunk == null) return "";
        StringBuilder b = new StringBuilder();

        // Flora — plants are in plain sight; a keener eye names more kinds.
        int floraCap = acuity >= 0.6 ? 4 : acuity >= 0.3 ? 3 : 2;
        List<String> flora = orEmpty(jdbc.query(
            "SELECT flora_key FROM chunk_flora WHERE chunk_id=? AND quantity>0 ORDER BY quantity DESC LIMIT " + floraCap,
            (rs, i) -> humanize(rs.getString(1)), chunk));
        if (!flora.isEmpty())
            b.append(capitalize(joinAnd(flora))).append(flora.size() == 1 ? " grows within reach. " : " grow within reach. ");

        // Wildlife — the conspicuous graze in the open; the shy leave only sign; a keener eye reads more.
        List<Map<String, Object>> animals = orEmpty(jdbc.queryForList(
            "SELECT wp.species_key, wp.ecological_role, COALESCE(ws.size_tier,'SMALL') AS size_tier, wp.activity_cycle " +
            "FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
            "LEFT JOIN wildlife_species ws ON ws.species_key=wp.species_key " +
            "WHERE es.chunk_id=? AND wp.population_count>0 ORDER BY wp.population_count DESC", chunk));
        int named = 0, cap = acuity >= 0.6 ? 3 : acuity >= 0.3 ? 2 : 1;
        for (Map<String, Object> a : animals) {
            if (named >= cap) break;
            double vis = visibility((String) a.get("size_tier"), (String) a.get("ecological_role"), (String) a.get("activity_cycle"));
            String name = humanize((String) a.get("species_key"));
            if (vis >= 1.0 - acuity) { b.append(seenLine(name, (String) a.get("ecological_role"))); named++; }
            else if (acuity >= 0.5) { b.append("You find the sign of ").append(name).append(" — tracks pressed into the ground. "); named++; }
        }

        // Fish — visible in the water to a moderate eye, where the biome holds them.
        if (acuity >= 0.4) {
            String biome = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, chunk);
            List<String> fish = orEmpty(jdbc.query(
                "SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE ? ORDER BY species_key LIMIT 1",
                (rs, i) -> humanize(rs.getString(1)), "%" + biome + "%"));
            if (!fish.isEmpty()) b.append("Fish hang in the water — ").append(fish.get(0)).append(" among them. ");
        }

        // Insects — hives and colonies announce themselves.
        List<String> colonies = orEmpty(jdbc.query(
            "SELECT colony_kind FROM insect_colony WHERE chunk_id=? ORDER BY colony_kind LIMIT 2",
            (rs, i) -> humanize(rs.getString(1)), chunk));
        for (String c : colonies) b.append("A ").append(c).append(" stirs nearby. ");

        return b.toString().trim();
    }

    /** The non-visual senses (#65): what the ground, air, water, fire, weather, and living things here give to
     *  ears, nose, and hands — or a careful search of the ground. Witness-stance, read-only, bounded by weather
     *  and perception; never a hint at an undiscovered recipe. */
    public enum Sense { SEARCH, LISTEN, SMELL, FEEL }

    @Transactional(readOnly = true)
    public String[] sense(UUID chronicle, UUID location, String actionText, Sense sense) {
        if (location == null) return new String[]{"SUCCEEDED", "Your senses find nothing here to fix on."};
        String biome = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : "unknown", location);
        String weather = jdbc.query("SELECT ww.weather_kind FROM world_weather ww JOIN world_chunk c ON c.world_id=ww.world_id WHERE c.id=?", rs -> rs.next() ? rs.getString(1) : null, location);
        boolean fire = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id WHERE w.current_location_id=? AND fs.active=true)", Boolean.class, location));
        boolean water = "WETLAND".equals(biome) || "RIVER_BANK".equals(biome) || Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM ecology_site WHERE chunk_id=? AND (site_kind ILIKE '%spring%' OR site_kind ILIKE '%stream%' OR site_kind ILIKE '%river%' OR site_kind ILIKE '%freshwater%'))", Boolean.class, location));
        boolean wet = "RAIN".equals(weather) || "STORM".equals(weather);
        double acuity = Math.min(1.0, 0.55 + capability.familiarity(chronicle, "ATTENTION"));
        StringBuilder b = new StringBuilder();
        switch (sense) {
            case LISTEN -> {
                b.append("You hold still and listen. ");
                if (wet) b.append("Rain hisses across leaf and ground. ");
                else if ("SNOW".equals(weather)) b.append("The snow deadens everything to a hush. ");
                else b.append(switch (biome == null ? "" : biome) {
                    case "TEMPERATE_FOREST" -> "Wind moves in the high branches. ";
                    case "GRASSLAND" -> "Wind combs steadily through the grass. ";
                    case "MOUNTAIN", "HIGHLAND" -> "The wind knocks about the bare rock. ";
                    default -> "The air is mostly still. "; });
                if (fire) b.append("Your fire ticks and settles close by. ");
                if (water) b.append("Somewhere near, water runs over stone. ");
                String life = presentLife(location, acuity);
                if (!life.isEmpty()) b.append(life);
            }
            case SMELL -> {
                b.append("You draw a slow breath through your nose. ");
                if (fire) b.append("Woodsmoke hangs sharp in the air. ");
                if (wet) b.append("The ground gives up a wet, mineral smell. ");
                if (water) b.append("There is the cool, green smell of open water. ");
                b.append(switch (biome == null ? "" : biome) {
                    case "TEMPERATE_FOREST" -> "Leaf mould and resin underlie it all. ";
                    case "GRASSLAND" -> "Crushed grass and sun-dried earth. ";
                    case "WETLAND" -> "Damp rot and standing water. ";
                    case "MOUNTAIN", "HIGHLAND" -> "Cold, thin air with little scent to it. ";
                    default -> "Little reaches you but cold air. "; });
                Integer carc = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE current_location_id=? AND object_type='CARCASS' AND lifecycle_state='ACTIVE'", Integer.class, location);
                if (carc != null && carc > 0) b.append("Under it runs the faint rot of something dead nearby. ");
            }
            case FEEL -> {
                Map<String, Object> item = resolveItem(chronicle, actionText == null ? "" : actionText.toLowerCase(Locale.ROOT));
                if (item != null) { b.append("You run your hands over it. ").append(inspectItem(item, tier(capability.familiarity(chronicle, "ATTENTION")))); }
                else {
                    b.append("You set your hand to the ground. ");
                    b.append(switch (biome == null ? "" : biome) {
                        case "TEMPERATE_FOREST" -> "It is soft with damp leaf litter. ";
                        case "GRASSLAND" -> "Dry grass over firm, warm earth. ";
                        case "MOUNTAIN", "HIGHLAND" -> "Cold, hard, gritted stone. ";
                        case "WETLAND" -> "Wet, yielding mud that clings. ";
                        default -> "Bare, ordinary ground. "; });
                    if (wet) b.append("Everything is slick and cold with rain. ");
                    else if ("SNOW".equals(weather)) b.append("The cold bites at your fingers. ");
                }
            }
            case SEARCH -> {
                b.append("You go over the ground with care. ");
                List<String> ground = orEmpty(jdbc.query(
                    "SELECT w.display_name FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                    "WHERE w.current_location_id=? AND w.current_owner_id IS NULL AND w.lifecycle_state='ACTIVE' ORDER BY w.display_name LIMIT 6",
                    (rs, i) -> rs.getString(1).toLowerCase(Locale.ROOT), location));
                if (!ground.isEmpty()) b.append("On the ground you turn up ").append(joinAnd(ground)).append(". ");
                String life = presentLife(location, acuity);
                if (!life.isEmpty()) b.append(life);
                if (ground.isEmpty() && (life == null || life.isEmpty())) b.append("Nothing here rewards the search. ");
            }
        }
        return new String[]{"SUCCEEDED", b.toString().trim()};
    }

    /** Estimation (#65 measure): weigh/heft or count a named reachable item, sound a water depth, or pace out a
     *  rough distance — bounded by having no instrument, so the figure is "near enough to plan by, not build to". */
    @Transactional(readOnly = true)
    public String[] measure(UUID chronicle, UUID location, String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains("weigh") || lower.contains("heavy") || lower.contains("heft") || lower.contains("how much does") || lower.contains("mass of")) {
            Map<String, Object> item = resolveItem(chronicle, lower);
            if (item == null) return new String[]{"SUCCEEDED", "You have nothing by that name in hand to weigh."};
            int g = ((Number) item.get("unit_mass_grams")).intValue();
            return new String[]{"SUCCEEDED", "You heft the " + ((String) item.get("display_name")).toLowerCase(Locale.ROOT) + " — " + heft(g) + " (about " + g + " g by a careful reckoning)."};
        }
        if (lower.contains("count") || lower.contains("how many") || lower.contains("tally")) {
            Map<String, Object> item = resolveItem(chronicle, lower);
            if (item == null) return new String[]{"SUCCEEDED", "You look over what you carry, but nothing by that name is here to count."};
            String key = (String) item.get("item_key"); String name = ((String) item.get("display_name")).toLowerCase(Locale.ROOT);
            Integer n = jdbc.queryForObject(
                "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' " +
                "UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') " +
                "SELECT COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key=?", Integer.class, chronicle, key);
            int c = n == null ? 0 : n;
            return new String[]{"SUCCEEDED", "You count " + c + " " + name + (c == 1 ? "" : "s") + " within reach."};
        }
        if (lower.contains("depth") || lower.contains("how deep") || lower.contains("sound the")) {
            String biome = jdbc.query("SELECT biome FROM world_chunk WHERE id=?", rs -> rs.next() ? rs.getString(1) : "", location);
            boolean water = "WETLAND".equals(biome) || "RIVER_BANK".equals(biome) || Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ecology_site WHERE chunk_id=? AND (site_kind ILIKE '%spring%' OR site_kind ILIKE '%stream%' OR site_kind ILIKE '%river%' OR site_kind ILIKE '%freshwater%'))", Boolean.class, location));
            return water ? new String[]{"SUCCEEDED", "You sound the water with a stick — it shelves off gradually, past a safe wade before long."}
                         : new String[]{"SUCCEEDED", "There is no standing water here to sound for depth."};
        }
        return new String[]{"SUCCEEDED", "You pace it out and reckon by eye. Without any instrument the figure is rough — near enough to plan by, not to build to."};
    }
    private static String heft(int g) {
        if (g < 100) return "next to nothing in the hand";
        if (g < 500) return "light";
        if (g < 1500) return "a fair, solid weight";
        if (g < 5000) return "heavy — a two-handed lift";
        return "a real burden, awkward to carry far";
    }

    private static <T> List<T> orEmpty(List<T> l) { return l == null ? List.of() : l; }
    private static String humanize(String key) { return key == null ? "" : key.replace('_', ' '); }
    private static String capitalize(String s) { return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static String joinAnd(List<String> xs) {
        if (xs.isEmpty()) return "";
        if (xs.size() == 1) return xs.get(0);
        if (xs.size() == 2) return xs.get(0) + " and " + xs.get(1);
        return String.join(", ", xs.subList(0, xs.size() - 1)) + ", and " + xs.get(xs.size() - 1);
    }
    private static double visibility(String size, String role, String activity) {
        double v = switch (size == null ? "" : size) {
            case "HUGE" -> 1.0; case "LARGE" -> 0.85; case "MEDIUM" -> 0.6; case "SMALL" -> 0.4; case "TINY" -> 0.25; default -> 0.4; };
        if ("CARNIVORE".equals(role)) v -= 0.15; else if ("HERBIVORE".equals(role)) v += 0.1;
        if ("NOCTURNAL".equals(activity)) v -= 0.25; else if ("CREPUSCULAR".equals(activity)) v -= 0.1;
        return Math.max(0, Math.min(1, v));
    }
    private static String seenLine(String name, String role) {
        return switch (role == null ? "" : role) {
            case "HERBIVORE" -> "A " + name + " grazes the open ground. ";
            case "CARNIVORE" -> "A " + name + " moves along the treeline, watchful. ";
            case "OMNIVORE" -> "A " + name + " forages nearby. ";
            default -> "A " + name + " is here. ";
        };
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
        b.append(features(it.get("object_id")));
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
        b.append(modificationHistory(it.get("object_id"))).append(features(it.get("object_id")));
        if (tier >= 3) b.append(" You are as sure of this as of anything you have not seen with your own eyes.");
        return b.toString();
    }

    /** The dated improvements a specific object has taken over its life (V68 object_modification) — its evolving story. */
    private String modificationHistory(Object objectId) {
        List<String> notes = orEmpty(jdbc.query(
            "SELECT note FROM object_modification WHERE object_id=? ORDER BY occurred_at DESC LIMIT 3",
            (rs, i) -> rs.getString(1), objectId));
        return notes.isEmpty() ? "" : " It carries the marks of later work: " + joinAnd(notes) + ".";
    }

    /** The bespoke features a specific object carries (V68 object_attribute) — a handle, a holder, a measured dimension. */
    private String features(Object objectId) {
        List<String> feats = orEmpty(jdbc.query(
            "SELECT attr_key, attr_value FROM object_attribute WHERE object_id=? ORDER BY attr_key",
            (rs, i) -> "true".equalsIgnoreCase(rs.getString(2)) ? humanize(rs.getString(1)) : humanize(rs.getString(1)) + " " + rs.getString(2), objectId));
        return feats.isEmpty() ? "" : " Its making shows " + joinAnd(feats) + ".";
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
