package com.devosphere.draugr.skill;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SKILLS, AND THE APTITUDES THAT SUPPORT THEM (GitHub #954).
 *
 * <p>{@code CapabilityAdaptationService} already grows eight broad numbers from practice. At that grain,
 * sixty-seven intents share FINE_MOTOR and forty-seven share ATTENTION, so a chronicle who has woven a
 * hundred baskets is — to the simulation — exactly as practised at writing, cooking and skinning. This
 * class is the specific layer that makes a weaver a weaver.
 *
 * <p>THE ONE RULE THAT MATTERS is the asymmetry. Practice develops the SKILL strongly and its supporting
 * APTITUDES slowly; aptitudes then assist every skill they support, but they never confer its mastery.
 * An archer's practice makes their aim a little better, their aim helps them throw a javelin, and they
 * are still not a javelin thrower.
 *
 * <p>NO FEEDBACK LOOP IS POSSIBLE, by construction rather than by care. {@link #practise} computes the
 * gain from the event alone — its span, its difficulty, the state of the body, and how much of this one
 * skill is already held — and never reads an aptitude. The aptitude credit is then a fixed share of that
 * gain. Growth therefore flows practice → skill → aptitude and cannot flow back, so the loop #954 warns
 * about has nowhere to close. Aptitudes are read only by {@link #competence}, which decides how well the
 * work goes and writes nothing.
 *
 * <p>Everything here is hidden. There is no player-facing level, no number on the Body HUD, and no
 * progression summary; what the player gets is the work going better and a sentence saying so.
 */
@Service
public class SkillProgressionService {

    /** How much of a practice event's gain is shared out among the supporting aptitudes. */
    private static final double APTITUDE_SHARE_OF_GAIN = 0.25;
    /** The most one act can add to one skill, however long or hard it was. No single afternoon is a career. */
    private static final double MOST_FROM_ONE_ACT = 0.02;
    /** The scale of a practice event: a well-judged half hour of hard new work, at 0 proficiency. */
    private static final double BASE_GAIN = 0.010;

    private final JdbcTemplate jdbc;

    public SkillProgressionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * What one practice event did, for the narration and for the causal record.
     *
     * @param skill     the discipline practised
     * @param before    proficiency before the act (0..1)
     * @param after     proficiency after it
     * @param aptitudes the credit each supporting aptitude received from this same event
     */
    public record Practice(String skill, double before, double after, Map<String, Double> aptitudes) {
        /** The gain, which is what a narration wants rather than the two endpoints. */
        public double gain() { return after - before; }
    }

    /**
     * WHICH SKILL AN ACT PRACTISED, asked of the data in the order of precision: the weapon in hand
     * beats the process run, which beats the intent's own card.
     *
     * <p>That order is #954's "derive both changes from the actual practice event". A fight is about the
     * weapon actually held, not about hunting in general; a making is about the process actually run,
     * because PROCESS_MATERIAL alone stands behind a hundred and ninety-three of them; and everything
     * else is what the intent says it is.
     *
     * @param processKey the catalogued process this act ran, or null if it ran none
     * @param weaponSkill the discipline of the weapon in hand, or null if this was not a fight
     * @return the skill key, or null when the act is one of the fifteen with no discipline to acquire
     */
    @Transactional(readOnly = true)
    public String disciplineOf(String intentKey, String processKey, String weaponSkill) {
        if (weaponSkill != null && !weaponSkill.isBlank()) return weaponSkill;
        if (processKey != null && !processKey.isBlank()) {
            String fromProcess = jdbc.query("SELECT skill_key FROM material_process WHERE process_key = ?",
                rs -> rs.next() ? rs.getString(1) : null, processKey);
            if (fromProcess != null) return fromProcess;
        }
        return jdbc.query("SELECT skill_key FROM activity_impact WHERE intent_key = ?",
            rs -> rs.next() ? rs.getString(1) : null, intentKey);
    }

    /**
     * The discipline of the weapon this chronicle would fight with, or null if they hold none.
     *
     * <p>Read from {@code weapon_profile.combat_skill_key} so the answer is the catalogue's, not a list
     * written in Java that a new weapon would not join. An equipped weapon is preferred over a carried
     * one, and among equals the keenest edge, because that is the one a person actually raises.
     */
    @Transactional(readOnly = true)
    public String weaponInHand(UUID chronicle) {
        return jdbc.query(
            "SELECT w.combat_skill_key FROM world_object o " +
            "  JOIN item_instance i ON i.object_id = o.id " +
            "  JOIN weapon_profile w ON w.item_key = i.item_key " +
            "  LEFT JOIN equipment_attachment e ON e.item_id = o.id AND e.chronicle_id = ? " +
            " WHERE o.current_owner_id = ? AND o.lifecycle_state = 'ACTIVE' " +
            "   AND w.combat_role <> 'ARROW' " +
            " ORDER BY (e.item_id IS NOT NULL) DESC, " +
            "          CASE w.edge_tier WHEN 'STEEL' THEN 5 WHEN 'IRON' THEN 4 WHEN 'BRONZE' THEN 3 " +
            "                           WHEN 'HARDENED' THEN 2 ELSE 1 END DESC " +
            " LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, chronicle, chronicle);
    }

    /**
     * RECORD ONE PRACTICE EVENT, and return what it changed.
     *
     * <p>The gain answers #954's list of what must matter, each from the event itself:
     * <ul>
     *   <li>DIFFICULTY — a hard task teaches more than an easy one;</li>
     *   <li>MEANINGFUL REPETITION — the thousandth basket teaches almost nothing, through {@code novelty};</li>
     *   <li>DIMINISHING RETURNS — through {@code headroom}, which falls away as mastery is approached;</li>
     *   <li>FATIGUE, INJURY AND ILLNESS — a body that is spent, hurt or sick learns badly;</li>
     *   <li>the SPAN of the work, bounded so a long act cannot buy a career.</li>
     * </ul>
     *
     * <p>Returns null when nothing was recorded — an unknown skill, or a replay of an action already
     * credited. The second case is enforced by a UNIQUE index rather than trusted to this code: the
     * insert is attempted and a conflict means the award has already been made, so a caller that asks
     * twice for the same action cannot be paid twice.
     *
     * @param difficulty 0..1, how demanding this particular act was
     */
    @Transactional
    public Practice practise(UUID chronicle, UUID action, String skill, int minutes, double difficulty, Instant at) {
        if (skill == null || skill.isBlank() || minutes <= 0) return null;
        Double weightTotal = jdbc.query("SELECT sum(weight) FROM skill_aptitude_support WHERE skill_key = ?",
            rs -> rs.next() ? (Double) (rs.getObject(1) == null ? null : rs.getDouble(1)) : null, skill);
        if (weightTotal == null || weightTotal <= 0) return null;   // not a catalogued skill

        jdbc.update("INSERT INTO chronicle_skill_practice (chronicle_id, skill_key) VALUES (?, ?) " +
                    "ON CONFLICT (chronicle_id, skill_key) DO NOTHING", chronicle, skill);
        double[] held = jdbc.query(
            "SELECT proficiency, practice_events FROM chronicle_skill_practice WHERE chronicle_id = ? AND skill_key = ?",
            rs -> rs.next() ? new double[]{rs.getDouble(1), rs.getDouble(2)} : new double[]{0, 0}, chronicle, skill);
        double before = held[0];
        double events = held[1];

        double diff = Math.max(0.0, Math.min(1.0, difficulty));
        double headroom = Math.pow(1.0 - before, 1.5);                 // mastery is approached, never arrived at
        double novelty  = 1.0 / (1.0 + events / 40.0);                 // familiar easy work pays less
        double effort   = Math.max(0.2, Math.min(2.0, minutes / 30.0));
        double demand   = 0.35 + 0.65 * diff;
        double fit      = bodyFitForLearning(chronicle);
        double gain     = Math.min(MOST_FROM_ONE_ACT, BASE_GAIN * headroom * novelty * effort * demand * fit);
        if (gain <= 0) return null;
        double after = Math.min(1.0, before + gain);

        // The supporting aptitudes take a fixed share of THIS gain, split by their catalogued weights.
        // Normalised by the weights' own total, so a drift in the data cannot widen the share.
        Map<String, Double> shares = new LinkedHashMap<>();
        StringBuilder sharesJson = new StringBuilder("{");
        jdbc.query("SELECT a.aptitude_key, a.adaptation_column, s.weight FROM skill_aptitude_support s " +
                   "JOIN aptitude a ON a.aptitude_key = s.aptitude_key WHERE s.skill_key = ? ORDER BY s.weight DESC",
            rs -> {
                double credit = APTITUDE_SHARE_OF_GAIN * gain * (rs.getDouble(3) / weightTotal);
                String column = rs.getString(2);
                // The column name comes from the aptitude catalogue, whose adaptation_column is UNIQUE and
                // written in this migration — never from anything a player typed.
                jdbc.update("UPDATE chronicle_capability_adaptation SET " + column + " = LEAST(1, " + column + " + ?) " +
                            "WHERE chronicle_id = ?", credit, chronicle);
                shares.put(rs.getString(1), credit);
                if (sharesJson.length() > 1) sharesJson.append(',');
                sharesJson.append('"').append(rs.getString(1)).append("\":").append(credit);
            }, skill);
        sharesJson.append('}');

        // THE AWARD AND ITS EVIDENCE, in that order: if the evidence row conflicts, this action has
        // already been credited and the practice row must not move either.
        int wrote = jdbc.update(
            "INSERT INTO chronicle_skill_practice_event " +
            "(chronicle_id, action_id, occurred_at, skill_key, exposure_minutes, difficulty, " +
            " proficiency_before, proficiency_after, aptitude_shares) " +
            "VALUES (?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",
            chronicle, action, Timestamp.from(at), skill, minutes, diff, before, after, sharesJson.toString());
        if (wrote == 0) return null;                                   // already credited for this action

        jdbc.update("UPDATE chronicle_skill_practice SET proficiency = ?, practice_events = practice_events + 1, " +
                    "practised_minutes = practised_minutes + ?, last_practised_at = ? " +
                    "WHERE chronicle_id = ? AND skill_key = ?",
            after, minutes, Timestamp.from(at), chronicle, skill);
        return new Practice(skill, before, after, shares);
    }

    /**
     * How fit this body is to learn right now, 0.35..1.0 — #954's "account for fatigue, injury, and
     * recovery". Exhaustion, pain, an open injury and illness all blunt what an hour teaches; none of
     * them stops it entirely, because people do learn things badly and still learn them.
     */
    @Transactional(readOnly = true)
    public double bodyFitForLearning(UUID chronicle) {
        Double fit = jdbc.query(
            "SELECT GREATEST(0.35, 1.0 " +
            "   - CASE WHEN energy_level < 25 THEN 0.30 WHEN energy_level < 50 THEN 0.12 ELSE 0 END " +
            "   - LEAST(0.25, injury_severity / 400.0) " +
            "   - LEAST(0.20, illness_severity / 500.0) " +
            "   - LEAST(0.15, GREATEST(0, sleep_debt_hours - 16) / 160.0) " +
            "   - LEAST(0.10, pain_level / 1000.0)) " +
            " FROM chronicle_physiology WHERE chronicle_id = ?",
            rs -> rs.next() ? rs.getDouble(1) : null, chronicle);
        return fit == null ? 1.0 : fit;
    }

    /** What this chronicle has actually practised at this skill, 0..1. 0 when they never have. */
    @Transactional(readOnly = true)
    public double proficiency(UUID chronicle, String skill) {
        Double v = jdbc.query("SELECT proficiency FROM chronicle_skill_practice WHERE chronicle_id = ? AND skill_key = ?",
            rs -> rs.next() ? rs.getDouble(1) : null, chronicle, skill);
        return v == null ? 0.0 : v;
    }

    /**
     * HOW WELL THIS CHRONICLE DOES THIS WORK, 0..1 — the number performance reads.
     *
     * <p>Mostly what they have practised, and partly what their body and mind bring to it. The aptitude
     * share is deliberately bounded and deliberately real: a strong, observant novice does better at
     * their first felling than a weak unobservant one, and neither of them is a woodsman. That is
     * #954's "bounded assistance", and it is why practising weaving helps the next carving a little
     * without ever making the weaver a carver.
     *
     * <p>A skill not in the catalogue returns 0 rather than faulting mid-action.
     */
    @Transactional(readOnly = true)
    public double competence(UUID chronicle, String skill) {
        if (skill == null || skill.isBlank()) return 0.0;
        // The aptitude catalogue says which column holds each faculty, so the mapping lives in exactly one
        // place. Reading it here rather than restating it as a CASE keeps the second reader from drifting
        // from the first — the defect this project keeps finding in itself.
        Map<String, Double> weightByColumn = new LinkedHashMap<>();
        jdbc.query("SELECT a.adaptation_column, s.weight FROM skill_aptitude_support s " +
                   "JOIN aptitude a ON a.aptitude_key = s.aptitude_key WHERE s.skill_key = ?",
            rs -> { weightByColumn.merge(rs.getString(1), rs.getDouble(2), Double::sum); }, skill);
        double practised = proficiency(chronicle, skill);
        if (weightByColumn.isEmpty()) return Math.min(1.0, 0.65 * practised);

        String columns = String.join(", ", weightByColumn.keySet());
        Map<String, Double> held = jdbc.query(
            "SELECT " + columns + " FROM chronicle_capability_adaptation WHERE chronicle_id = ?",
            rs -> {
                Map<String, Double> m = new LinkedHashMap<>();
                if (rs.next()) { int i = 1; for (String c : weightByColumn.keySet()) m.put(c, rs.getDouble(i++)); }
                return m;
            }, chronicle);

        double weightTotal = weightByColumn.values().stream().mapToDouble(Double::doubleValue).sum();
        double support = 0;
        for (Map.Entry<String, Double> e : weightByColumn.entrySet())
            support += e.getValue() * held.getOrDefault(e.getKey(), 0.0);
        support = weightTotal > 0 ? support / weightTotal : 0;
        return Math.max(0.0, Math.min(1.0, 0.65 * practised + 0.35 * support));
    }

    /**
     * HOW DEMANDING THIS ACT WAS, 0..1 — read from what the catalogue already decided, never guessed.
     *
     * <p>#954: "Account for difficulty... Familiar easy tasks should have diminishing returns." Difficulty
     * is deliberately NOT the clock: how long an act took is already in the gain as {@code effort}, and
     * counting a long easy haul as hard work would pay twice for the same hour.
     *
     * <p>What it is instead: {@code activity_impact.labor_energy}, the per-intent cost someone decided when
     * the impact cards were written (0 for looking at the sky, 12 for felling a tree); and where a
     * catalogued process ran, whatever that process demands — a tool, a fire, a workstation, or an hour
     * and a half of it. The harder of the two answers stands, because a hard process done under an easy
     * intent is still hard work.
     */
    @Transactional(readOnly = true)
    public double difficultyOf(String intentKey, String processKey) {
        Double fromIntent = jdbc.query("SELECT labor_energy / 12.0 FROM activity_impact WHERE intent_key = ?",
            rs -> rs.next() ? rs.getDouble(1) : null, intentKey);
        double worst = fromIntent == null ? 0.3 : fromIntent;
        if (processKey != null && !processKey.isBlank()) {
            Double fromProcess = jdbc.query(
                "SELECT LEAST(1.0, 0.30 " +
                "  + CASE WHEN tool_class   IS NOT NULL THEN 0.20 ELSE 0 END " +
                "  + CASE WHEN requires_fire              THEN 0.20 ELSE 0 END " +
                "  + CASE WHEN station_kind IS NOT NULL THEN 0.15 ELSE 0 END " +
                "  + LEAST(0.15, COALESCE(duration_minutes,0) / 600.0)) " +
                " FROM material_process WHERE process_key = ?",
                rs -> rs.next() ? rs.getDouble(1) : null, processKey);
            if (fromProcess != null) worst = Math.max(worst, fromProcess);
        }
        return Math.max(0.15, Math.min(1.0, worst));
    }

    /** The thresholds at which a chronicle notices the work going better. */
    private static final double[] BANDS = {0.05, 0.12, 0.25, 0.45, 0.70};

    private static int bandOf(double proficiency) {
        int b = 0;
        for (double threshold : BANDS) if (proficiency >= threshold) b++;
        return b;
    }

    /**
     * A SENTENCE WHEN THE WORK HAS VISIBLY CHANGED, or null — which is almost always.
     *
     * <p>#954: "Keep progression metrics hidden from the Body HUD. Narrate concrete improvements such as
     * steadier aim, cleaner cuts, or reduced wasted effort." So no number ever reaches the player. What
     * reaches them is this, on the handful of acts that cross a threshold: a plain observation about
     * their own hands, in the register the rest of the prose uses.
     *
     * <p>Phrased by family rather than by skill, with the discipline named, because thirty-eight bespoke
     * sentences would drift out of step with the catalogue the first time a skill was added.
     */
    @Transactional(readOnly = true)
    public String improvementSentence(Practice practice) {
        if (practice == null) return null;
        int was = bandOf(practice.before()), now = bandOf(practice.after());
        if (now <= was) return null;
        String family = jdbc.query("SELECT family FROM skill_definition WHERE skill_key = ?",
            rs -> rs.next() ? rs.getString(1) : null, practice.skill());
        String name = jdbc.query("SELECT lower(display_name) FROM skill_definition WHERE skill_key = ?",
            rs -> rs.next() ? rs.getString(1) : null, practice.skill());
        if (family == null) return null;
        boolean early = now <= 2;
        return switch (family) {
            case "CRAFT" -> early
                ? " Your hands find the work without being told this time — the cuts come cleaner, and less of the stock goes to waste."
                : " You work at it the way someone does who has done it often: no hesitation, little waste, and the result would pass anywhere.";
            case "COMBAT" -> early
                ? " Something has settled in how you hold it. The weapon goes where you meant it to rather than where it happened to go."
                : " You handle it without thinking about handling it. Your " + name + " is no longer a thing you are attempting.";
            case "LAND" -> early
                ? " You are reading the ground better than you were. Less of the effort goes nowhere."
                : " The work costs you less than it did. You know where to put your hands, and where not to.";
            case "ANIMAL" -> early
                ? " You are beginning to know what the animals do before they do it, and to move accordingly."
                : " You move around them like someone they are used to, and the work goes quietly.";
            case "MIND" -> early
                ? " The work comes more easily than it did — your marks are steadier, and you second-guess them less."
                : " You set it down almost as fast as you think it, and what you set down holds up when you come back to it.";
            case "BODY" -> early
                ? " Your body does this more readily than it used to. It costs you less to get it right."
                : " You do it without thinking, and well.";
            case "SOCIAL" -> early
                ? " You are getting better at reading people — when to speak, and when the answer has already been given."
                : " You know how this goes now, and you carry yourself like it.";
            default -> null;
        };
    }
}
