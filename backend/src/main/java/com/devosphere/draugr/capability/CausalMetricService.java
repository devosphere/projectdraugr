package com.devosphere.draugr.capability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * WHAT CHANGED, AND WHY (GitHub #953).
 *
 * <p>Every write to the eight hidden numbers in {@code chronicle_capability_adaptation} had been blind:
 * {@code SET load_conditioning = LEAST(1, load_conditioning + ?)}. The value before the change was never
 * read, so it was never recorded, so nothing anywhere could say what a Chronicle's conditioning WAS, what
 * moved it, or by how much. {@code chronicle_capability_evidence} kept the cause — the domain, the minutes,
 * the load — and threw the effect away, which is the wrong half.
 *
 * <p>This is the other half. One place writes a tracked metric, and writing it records before, after, delta,
 * cause, the action or world event responsible, and the simulated hour. A change nothing can account for is
 * not supposed to be possible any more.
 *
 * <p>AND NOTHING EVER DECLINED. Seven of the eight columns were only ever written as {@code col + delta}, so
 * a Chronicle who hauled timber for a week and then sat by the fire for a month was exactly as conditioned
 * as the day they stopped. {@link #letTheUnpractisedSlip} is the other side of that: what is not used fades,
 * at the rate each metric's own catalogue row sets, and the fading is recorded with cause NEGLECT like
 * anything else.
 *
 * <p>Everything here is hidden. Nothing reaches the Body HUD; what reaches the player is
 * {@link #whatIsFelt}, which speaks only when a metric has moved far enough to be worth noticing.
 */
@Service
public class CausalMetricService {

    /** How long a metric may go unpractised before neglect starts to tell. */
    private static final Duration GRACE = Duration.ofDays(7);

    /** Neglect is settled at most this often, so one idle day is one ledger row, not one per action. */
    private static final Duration SETTLES_EVERY = Duration.ofDays(1);

    private final JdbcTemplate jdbc;

    public CausalMetricService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** One recorded movement of one hidden number. */
    public record Change(String metricKey, double before, double after, String cause) {
        public double delta() { return after - before; }
        public boolean rose() { return after > before; }
    }

    /**
     * MOVE A TRACKED METRIC BY {@code delta}, AND RECORD WHY.
     *
     * <p>The column is taken from {@code metric_subject}, whose {@code (source_table, source_column)} is
     * UNIQUE and written in the migration — never from anything a player typed. The value is clamped to
     * 0..1 and the row is written only if the number actually moved, because a recorded change of nothing
     * is noise that would bury the real ones.
     *
     * @return what changed, or null if the metric is unknown or the value did not move
     */
    @Transactional
    public Change move(UUID chronicle, String metricKey, double delta, String cause,
                       UUID sourceAction, String sourceEvent, Integer exposureMinutes,
                       Double recoveryContext, Instant at) {
        if (chronicle == null || metricKey == null || delta == 0) return null;
        String[] where = jdbc.query(
            "SELECT source_table, source_column FROM metric_subject WHERE metric_key = ?",
            rs -> rs.next() ? new String[]{rs.getString(1), rs.getString(2)} : null, metricKey);
        if (where == null) return null;

        jdbc.update("INSERT INTO chronicle_capability_adaptation (chronicle_id) VALUES (?) ON CONFLICT DO NOTHING", chronicle);
        Double held = jdbc.query("SELECT " + where[1] + " FROM " + where[0] + " WHERE chronicle_id = ?",
            rs -> rs.next() ? rs.getDouble(1) : null, chronicle);
        if (held == null) return null;

        double before = held;
        double after = Math.max(0.0, Math.min(1.0, before + delta));
        // Clamping at the ceiling or the floor means the number did not move, and a Chronicle who is already
        // at their limit has not strengthened again. Nothing is recorded, so nothing is narrated.
        if (Math.abs(after - before) < 1e-9) return null;

        jdbc.update("UPDATE " + where[0] + " SET " + where[1] + " = ? WHERE chronicle_id = ?", after, chronicle);
        jdbc.update(
            "INSERT INTO chronicle_metric_change (chronicle_id, metric_key, occurred_at, before_value, " +
            " after_value, delta, cause, source_action_id, source_event, exposure_minutes, recovery_context) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            chronicle, metricKey, Timestamp.from(at), before, after, after - before, cause,
            sourceAction, sourceAction == null && sourceEvent == null ? "unattributed" : sourceEvent,
            exposureMinutes, recoveryContext);
        return new Change(metricKey, before, after, cause);
    }

    /**
     * RECORD A CHANGE SOMEBODY ELSE APPLIED (#960).
     *
     * <p>{@link #move} is the one place that both moves a metric and accounts for it, and it stays that way.
     * But the physiology tick computes a whole body in one pass and writes it in one statement -- digestion,
     * expenditure, the cold, the fever and what they did to the store, all together -- and it cannot
     * sensibly hand each of those to a read-modify-write of its own. It has the before and the after; what
     * it needs is somewhere to put the account.
     *
     * <p>So this writes the ledger row and touches no column. Nothing is recorded if the value did not move.
     *
     * @return what was recorded, or null if nothing moved or the metric is not tracked
     */
    @Transactional
    public Change recordApplied(UUID chronicle, String metricKey, double before, double after, String cause,
                               UUID sourceAction, String sourceEvent, Integer exposureMinutes, Instant at) {
        if (chronicle == null || metricKey == null || Math.abs(after - before) < 1e-9) return null;
        Boolean tracked = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM metric_subject WHERE metric_key = ?)", Boolean.class, metricKey);
        if (!Boolean.TRUE.equals(tracked)) return null;
        jdbc.update(
            "INSERT INTO chronicle_metric_change (chronicle_id, metric_key, occurred_at, before_value, " +
            " after_value, delta, cause, source_action_id, source_event, exposure_minutes) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?)",
            chronicle, metricKey, Timestamp.from(at), before, after, after - before, cause,
            sourceAction, sourceAction == null && sourceEvent == null ? "the passing of time" : sourceEvent,
            exposureMinutes);
        return new Change(metricKey, before, after, cause);
    }

    /**
     * WHAT IS NOT USED FADES (#953).
     *
     * <p>Each metric's own catalogue row says how fast it slips per idle week, and the ledger says when it
     * was last genuinely practised — {@code MAX(occurred_at)} over its PRACTICE rows. So neglect is measured
     * per metric without eight more columns to keep in step, and the record and the mechanic are the same
     * fact, which is the only way they cannot drift apart.
     *
     * <p>The slip is recorded as a change with cause NEGLECT, so a decline is as reconstructable as a gain.
     * Hands fade slowest and aim fastest, which is the way of it.
     *
     * @return every metric that actually slipped
     */
    @Transactional
    public List<Change> letTheUnpractisedSlip(UUID chronicle, Instant now) {
        List<Change> slipped = new ArrayList<>();
        List<java.util.Map<String, Object>> idle = jdbc.queryForList(
            "SELECT m.metric_key, m.slips_per_idle_week, " +
            "       COALESCE((SELECT max(c.occurred_at) FROM chronicle_metric_change c " +
            "                  WHERE c.chronicle_id = ? AND c.metric_key = m.metric_key AND c.cause = 'PRACTICE'), " +
            "                (SELECT ch.arrived_at FROM chronicle ch WHERE ch.id = ?)) AS last_worked, " +
            "       (SELECT max(c.occurred_at) FROM chronicle_metric_change c " +
            "         WHERE c.chronicle_id = ? AND c.metric_key = m.metric_key AND c.cause = 'NEGLECT') AS last_slipped " +
            "  FROM metric_subject m WHERE m.slips_per_idle_week > 0", chronicle, chronicle, chronicle);

        for (java.util.Map<String, Object> row : idle) {
            Object when = row.get("last_worked");
            if (when == null) continue;
            Instant lastWorked = ((Timestamp) when).toInstant();
            Duration unpractised = Duration.between(lastWorked, now);
            if (unpractised.compareTo(GRACE) <= 0) continue;

            // Only the time BEYOND the grace counts, so the slip starts gently rather than all at once on
            // the eighth day.
            //
            // AND ONLY THE TIME NOT ALREADY CHARGED. This is asked on every resolved action, and the idle span
            // measured from the last practice is the same span each time it is asked — so ten acts in one
            // afternoon, a fortnight after the bow was last drawn, took ten fortnights off the aim. A NEGLECT
            // row is the record that a stretch of idleness has been paid for; the slip runs from the later of
            // the grace's end and the last time it was charged, so a week of neglect costs a week of slip
            // however often the world looks. It settles at most once a simulated day, so the ledger holds one
            // neglect row per metric per idle day rather than one per action.
            Instant chargedFrom = lastWorked.plus(GRACE);
            Object slippedAt = row.get("last_slipped");
            if (slippedAt != null) {
                Instant lastSlipped = ((Timestamp) slippedAt).toInstant();
                if (lastSlipped.isAfter(chargedFrom)) chargedFrom = lastSlipped;
            }
            if (Duration.between(chargedFrom, now).compareTo(SETTLES_EVERY) < 0) continue;
            double weeks = Duration.between(chargedFrom, now).toMinutes() / (7.0 * 24 * 60);
            double rate = ((Number) row.get("slips_per_idle_week")).doubleValue();
            Change change = move(chronicle, (String) row.get("metric_key"), -rate * weeks, "NEGLECT",
                null, "unpractised for " + unpractised.toDays() + " days", null, null, now);
            if (change != null) slipped.add(change);
        }
        return slipped;
    }

    /**
     * WHAT THE BODY HAS NOTICED, as sentences — or an empty list, which is the usual answer.
     *
     * <p>#953: "Avoid repetitive notices on every tiny change" and "avoid claims of adaptation without a
     * recorded mechanical effect." Both are satisfied the same way: the notice is drawn from the LEDGER, not
     * from a hopeful guess, and it speaks only once the untold changes in one metric sum to that metric's
     * own {@code felt_step}. A practice event moves a familiarity by about 0.002 and a step is 0.05, so this
     * is roughly twenty-five sessions of real work — a change a person would notice in their own hands.
     *
     * <p>Marking the rows told is the one thing that may be done to a recorded change, and only ever once.
     */
    @Transactional
    public List<String> whatIsFelt(UUID chronicle) {
        List<java.util.Map<String, Object>> ripe = jdbc.queryForList(
            "SELECT m.metric_key, m.display_name, sum(c.delta) AS moved " +
            "  FROM chronicle_metric_change c JOIN metric_subject m USING (metric_key) " +
            " WHERE c.chronicle_id = ? AND NOT c.told " +
            " GROUP BY m.metric_key, m.display_name, m.felt_step " +
            "HAVING abs(sum(c.delta)) >= m.felt_step", chronicle);

        List<String> told = new ArrayList<>();
        for (java.util.Map<String, Object> row : ripe) {
            String metric = (String) row.get("metric_key");
            double moved = ((Number) row.get("moved")).doubleValue();
            told.add(sentenceFor(metric, moved > 0));
            jdbc.update("UPDATE chronicle_metric_change SET told = TRUE " +
                        "WHERE chronicle_id = ? AND metric_key = ? AND NOT told", chronicle, metric);
        }
        return told;
    }

    /**
     * The felt effect, never the number (#953: "narrate meaningful changes through felt effects", "Keep
     * hidden values out of the Body HUD").
     *
     * <p>Written per metric and per direction, because "you are stronger" and "your hands are steadier" are
     * not the same sentence and a player who is told the generic one twice learns nothing from either.
     */
    public static String sentenceFor(String metricKey, boolean rose) {
        return switch (metricKey) {
            case "LOAD_CONDITIONING" -> rose
                ? " A load that had you stopping to shift it a week ago rides easier on you now."
                : " You notice the weight sooner than you used to. Work has not been asked of your back in a while, and it has taken the offer.";
            case "LOCOMOTION_FAMILIARITY" -> rose
                ? " Your feet are finding their own way over broken ground, without you looking down at every step."
                : " You have been picking your way more carefully than you once did. The ground has not changed.";
            case "FINE_MOTOR_FAMILIARITY" -> rose
                ? " Your hands have stopped fumbling at the small work. They go where you want them without being watched."
                : " The fine work is coming out clumsier than you remember it being.";
            case "VISUAL_AIM_FAMILIARITY" -> rose
                ? " Your eye and your hand are agreeing with each other more often than they were."
                : " Your aim has drifted off true, and you can feel that it has.";
            case "ATTENTION_RESILIENCE" -> rose
                ? " You are catching things at the edge of sight that you would have walked past before."
                : " Your attention wanders sooner than it did, and you have caught yourself missing what was in front of you.";
            case "INSIGHT_FAMILIARITY" -> rose
                ? " How a thing works is coming to you faster than it used to, as though you had seen its like before."
                : " Working a thing out is taking you longer than it once would have.";
            case "KNOWLEDGE_FAMILIARITY" -> rose
                ? " What you know is sitting closer to hand. Something half-remembered came back to you whole."
                : " Something you knew would not come when you reached for it.";
            case "ENERGY_RESERVES" -> rose
                ? " There is something behind you again when you set yourself to a thing, instead of only the will to start it."
                : " You are running on less than you were. The work takes what it takes and there is not much left over.";
            case "NUTRITION" -> rose
                ? " Whatever you have been eating has been the right of it. Your hair and your nails and the healing of small cuts all say so."
                : " What you have been living on is not enough, and it is beginning to show in your skin and your gums and how slowly the smallest scratch closes.";
            case "STARVATION_SEVERITY" -> rose
                ? " Your body has begun taking itself apart to keep going. You can feel where it is taking it from."
                : " Something deep is coming back. You are a long way from well, and you are further along than you were.";
            case "RECOVERY_READINESS" -> rose
                ? " Rest is doing more for you than it was. You wake further ahead than you lay down behind."
                : " Rest is not giving you back what it used to.";
            default -> rose ? " Something in you has strengthened." : " Something in you has slackened.";
        };
    }

    /** What is on record for one metric, for reconstructing a life afterwards. Newest first. */
    @Transactional(readOnly = true)
    public List<Change> history(UUID chronicle, String metricKey, int limit) {
        return jdbc.query(
            "SELECT metric_key, before_value, after_value, cause FROM chronicle_metric_change " +
            " WHERE chronicle_id = ? AND metric_key = ? ORDER BY occurred_at DESC, id DESC LIMIT ?",
            (rs, row) -> new Change(rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getString(4)),
            chronicle, metricKey, Math.max(1, Math.min(limit, 200)));
    }
}
