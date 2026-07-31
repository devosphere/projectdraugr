package com.devosphere.draugr.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether an unresolved action needs the Persistent State Architect at all.
 *
 * <p>The counterpart to {@code NarrationRouter}, and for the same reason. The
 * Architect's job is to add schema when a civilization invents something genuinely
 * new. It is not to re-derive what the world already knows. Asking it to verify
 * every craft, construction, or procedure would spend a large model on questions
 * the foundation can already answer for free — and would do it on the hot path of
 * ordinary play.
 *
 * <p>So the routing is a coverage question, answered against the foundation itself:
 *
 * <ul>
 *   <li>{@code COVERED} — a technique, material process, construction kind, or fire
 *       method already resolves this. No call. The engine simply does it.</li>
 *   <li>{@code POLISH} — the domain is registered and the materials exist, but no
 *       recipe joins them. The Architect is handed what is already there and asked
 *       to refine it into a concrete row, not to invent a world. Small prompt,
 *       small output, and the result is checkable against existing structure.</li>
 *   <li>{@code INVENT} — no domain, or the materials genuinely are not in the world.
 *       This is the rare case the Architect exists for: real schema evolution.</li>
 * </ul>
 *
 * <p>The practical claim behind this: any common Earth technology whose materials
 * are already in the world seed should be in the foundation before play, not
 * improvised per-session. Every such gap is a recurring API cost and, worse, a
 * source of data the Auditor then has to spend its own calls validating. Coverage
 * is cheaper than correction.
 */
@Component
public class ArchitectRouter {

    public enum Route {
        /** The foundation already resolves this. No AI call. */
        COVERED,
        /** Domain and materials exist; the Architect refines rather than invents. */
        POLISH,
        /** Genuinely new ground — the Architect must extend the schema. */
        INVENT
    }

    /**
     * @param route what to do
     * @param reason one line, for the log and for the Architect's own prompt
     * @param knownDomains domains already registered that bear on this request
     * @param availableMaterials item keys the world already defines that the text names
     */
    public record Assessment(Route route, String reason, List<String> knownDomains, List<String> availableMaterials) { }

    private final JdbcTemplate jdbc;
    public ArchitectRouter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Assess an action the deterministic dispatch could not resolve.
     *
     * <p>Read-only and cheap: four indexed lookups against catalogues that are small
     * and rarely change. It must stay far cheaper than the call it is deciding about.
     */
    @Transactional(readOnly = true)
    public Assessment assess(String actionText) {
        String raw = actionText == null ? "" : actionText.toLowerCase(Locale.ROOT);
        if (raw.isBlank()) return new Assessment(Route.COVERED, "Empty action; nothing to build.", List.of(), List.of());
        // Matching is on whole words, not substrings. Padding both the text and each
        // candidate with spaces makes " ash " fail to match "flashing" and " weather "
        // fail to match "weatherproof" — collisions that were silently routing real
        // gaps to COVERED, which is the dangerous direction: a false COVERED
        // suppresses the very Architect call the situation needed. Non-letters become
        // spaces so punctuation does not weld words together.
        String v = " " + raw.replaceAll("[^a-z0-9]+", " ").trim() + " ";

        // 1. Does something in the foundation already resolve this outright?
        List<String> covering = jdbc.queryForList(
            "SELECT display_name FROM (" +
            "  SELECT display_name, lower(display_name) AS m FROM technique_definition" +
            "  UNION ALL SELECT display_name, lower(display_name) FROM material_process" +
            "  UNION ALL SELECT display_name, lower(display_name) FROM construction_kind" +
            "  UNION ALL SELECT display_name, lower(display_name) FROM fire_method" +
            ") c WHERE ? LIKE '% ' || m || ' %' LIMIT 5", String.class, v);
        if (!covering.isEmpty())
            return new Assessment(Route.COVERED,
                "Already in the foundation: " + String.join(", ", covering) + ".", List.of(), List.of());

        // Keyword coverage too — a process is named by its verbs, not only its title.
        Integer kw = jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process mp, unnest(string_to_array(mp.keywords, ',')) k " +
            "WHERE ? LIKE '% ' || btrim(k) || ' %'", Integer.class, v);
        if (kw != null && kw > 0)
            return new Assessment(Route.COVERED, "A material process already covers this action.", List.of(), List.of());

        // 2. What does the world already have that bears on the request?
        List<String> materials = jdbc.queryForList(
            "SELECT item_key FROM item_definition WHERE ? LIKE '% ' || replace(item_key,'_',' ') || ' %' " +
            "   OR ? LIKE '% ' || lower(display_name) || ' %' LIMIT 12", String.class, v, v);
        List<String> domains = jdbc.queryForList(
            "SELECT domain_key FROM domain_registry WHERE ? LIKE '% ' || replace(domain_key,'_',' ') || ' %' " +
            "   OR ? LIKE '% ' || lower(display_name) || ' %' LIMIT 8", String.class, v, v);

        // 3. Materials present means the player can genuinely reach for this, so the
        // Architect is refining a gap between known things rather than inventing.
        if (!materials.isEmpty())
            return new Assessment(Route.POLISH,
                "Materials already exist in the world (" + String.join(", ", materials)
                    + "); the Architect should add the recipe that joins them, not a new domain.",
                domains, materials);
        if (!domains.isEmpty())
            return new Assessment(Route.POLISH,
                "Domain already registered (" + String.join(", ", domains)
                    + "); extend it rather than creating another.", domains, List.of());

        return new Assessment(Route.INVENT,
            "No domain, process, or material in the foundation matches this. Genuine schema evolution.",
            List.of(), List.of());
    }

    /**
     * A standing coverage report: how much of the world is answerable without the
     * Architect at all. Falling numbers here are the early warning that play is
     * drifting ahead of the foundation and API spend is about to rise.
     */
    @Transactional(readOnly = true)
    public Coverage coverage() {
        Integer domains = jdbc.queryForObject("SELECT COUNT(*) FROM domain_registry", Integer.class);
        Integer techniques = jdbc.queryForObject("SELECT COUNT(*) FROM technique_definition", Integer.class);
        Integer processes = jdbc.queryForObject("SELECT COUNT(*) FROM material_process", Integer.class);
        Integer items = jdbc.queryForObject("SELECT COUNT(*) FROM item_definition", Integer.class);
        Integer unreachable = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition d WHERE NOT EXISTS (SELECT 1 FROM item_source s WHERE s.item_key=d.item_key) " +
            "AND NOT EXISTS (SELECT 1 FROM item_unreachable_known k WHERE k.item_key=d.item_key)", Integer.class);
        return new Coverage(n(domains), n(techniques), n(processes), n(items), n(unreachable));
    }
    private static int n(Integer i) { return i == null ? 0 : i; }

    public record Coverage(int domains, int techniques, int processes, int items, int unreachableItems) { }
}
