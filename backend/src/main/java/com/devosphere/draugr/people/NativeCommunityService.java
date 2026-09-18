package com.devosphere.draugr.people;

import com.devosphere.draugr.item.PhysicalItemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A native community lives on the world clock (#111, epic #109).
 *
 * <p>Deterministic and compact, as the ticket asks: one step per simulated day per community, with no AI turn and no
 * per-individual scheduling. Each day the community's workers bring food into its store, everyone eats from the
 * store, and what that leaves decides how the community stands: fed and at its ordinary posture, hungry, closed to
 * visitors, or on the move. Every change of state is written to the community's history, which cannot be edited.
 *
 * <p>The food is real. Workers' takings are item instances held by the store's world object, spoiling on the same
 * clock as food anywhere; eating destroys the oldest sound item, one at a time. A store can therefore run out,
 * and that is the whole point: a people who can go hungry is a people whose goods are worth something to them, which
 * is what #113's trade will rest on.
 */
@Service
public class NativeCommunityService {

    /** Days simulated at most in one step: a world left alone for a year catches up in bounded work. */
    static final int MAX_DAYS_PER_STEP = 60;
    /** Days short before the community closes its store and its gates to outsiders. */
    static final int SHORTAGE_CLOSES = 3;
    /** Days short before the community leaves to find food elsewhere. */
    static final int SHORTAGE_MOVES = 14;

    private final JdbcTemplate jdbc;
    private final PhysicalItemService items;

    public NativeCommunityService(JdbcTemplate jdbc, PhysicalItemService items) {
        this.jdbc = jdbc;
        this.items = items;
    }

    /** Bring every living community up to {@code now}, a whole day at a time. */
    @Transactional
    public void advanceTo(Instant now) {
        List<Map<String, Object>> communities = jdbc.queryForList(
            "SELECT id, last_simulated_at FROM native_community WHERE lifecycle <> 'DISPERSED' ORDER BY founded_at, id FOR UPDATE");
        for (Map<String, Object> c : communities) {
            UUID id = (UUID) c.get("id");
            Instant last = ((Timestamp) c.get("last_simulated_at")).toInstant();
            long days = Duration.between(last, now).toDays();
            if (days <= 0) continue;
            long stepped = Math.min(days, MAX_DAYS_PER_STEP);
            for (long d = 1; d <= stepped; d++) liveADay(id, last.plus(Duration.ofDays(d)));
            // A world far behind is caught up to the present in one go; the days beyond the bound are not replayed,
            // they are simply not simulated, which is the same choice every other bounded catch-up here makes.
            Instant reached = days > MAX_DAYS_PER_STEP ? now : last.plus(Duration.ofDays(stepped));
            jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(reached), id);
        }
    }

    /** One day of one community: gather, eat, and stand accordingly. */
    void liveADay(UUID community, Instant day) {
        Map<String, Object> c = jdbc.queryForMap(
            "SELECT staple_item_key, daily_ration, shortage_days, lifecycle, base_trade_policy, base_security_posture, " +
            "       trade_policy, security_posture FROM native_community WHERE id=?", community);
        String staple = (String) c.get("staple_item_key");
        int ration = ((Number) c.get("daily_ration")).intValue();
        int shortage = ((Number) c.get("shortage_days")).intValue();

        UUID store = jdbc.query(
            "SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, community);

        // Gathering: those whose work is food bring in what the season gives. Hunger does not stop them — it is what
        // sends them out — but injury and sickness do, and the dead gather nothing.
        // A community with no standing store has nowhere to keep a take, so it lives hand to mouth: what it
        // gathers is eaten, never kept.
        int workers = count("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.role IN ('FORAGER','FISHER','HUNTER') AND n.condition IN ('WELL','HUNGRY') AND w.lifecycle_state='ACTIVE'", community);
        int gathered = workers * yieldPerWorker(day);
        String stapleName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, staple);
        if (store != null)
            for (int i = 0; i < gathered; i++) items.createHeldItem(store, staple, stapleName, day, "GATHERED_BY_COMMUNITY");

        // Eating: everyone living eats from the store, the soundest-oldest first. Food that has gone bad is not
        // eaten; it is thrown out, which is what a store-keeper does and what keeps the store honest.
        int eaters = count("SELECT COUNT(*) FROM native_individual n JOIN world_object w ON w.id=n.object_id " +
            "WHERE n.community_id=? AND n.condition <> 'DEAD' AND w.lifecycle_state='ACTIVE'", community);
        int need = eaters * ration;
        int eaten = 0;
        if (store != null) {
            for (UUID spoiled : jdbc.queryForList(
                    "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                    "JOIN food_preservation_state f ON f.object_id=w.id " +
                    "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND f.spoiled_at IS NOT NULL", UUID.class, store))
                items.retire(spoiled, day, "ROTTED", staple);
            List<UUID> larder = jdbc.queryForList(
                "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
                "LEFT JOIN food_preservation_state f ON f.object_id=w.id " +
                "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=? AND f.spoiled_at IS NULL " +
                "ORDER BY f.safe_until NULLS LAST, w.created_at LIMIT ?", UUID.class, store, staple, need);
            for (UUID item : larder) { items.retire(item, day, "EATEN_BY_COMMUNITY", staple); eaten++; }
        } else {
            eaten = Math.min(need, gathered);
        }

        boolean fed = eaten >= need;
        int nextShortage = fed ? 0 : shortage + 1;
        if (!fed && shortage == 0) record(community, day, "SHORTAGE_BEGAN", Map.of("ate", eaten, "needed", need));
        if (fed && shortage > 0) record(community, day, "SHORTAGE_ENDED", Map.of("days", shortage));
        jdbc.update("UPDATE native_individual SET condition = ? WHERE community_id=? AND condition = ?",
            fed ? "WELL" : "HUNGRY", community, fed ? "HUNGRY" : "WELL");
        jdbc.update("UPDATE native_community SET shortage_days=? WHERE id=?", nextShortage, community);

        // How the community stands. Closing up is what hungry people do with a store they cannot spare and ground
        // they cannot share; moving on is what they do when the ground has stopped feeding them. Recovery returns
        // them to what they are when fed, never past it.
        String trade = (String) c.get("trade_policy"), security = (String) c.get("security_posture");
        if (nextShortage >= SHORTAGE_CLOSES && !"CLOSED".equals(trade)) {
            jdbc.update("UPDATE native_community SET trade_policy='CLOSED', security_posture=CASE WHEN security_posture IN ('AT_EASE','WARY') THEN 'GUARDED' ELSE security_posture END WHERE id=?", community);
            jdbc.update("UPDATE native_settlement_site SET access_rule='CLOSED' WHERE community_id=?", community);
            record(community, day, "CLOSED_TO_OUTSIDERS", Map.of("shortageDays", nextShortage));
        } else if (nextShortage == 0 && (!trade.equals(c.get("base_trade_policy")) || !security.equals(c.get("base_security_posture")))) {
            jdbc.update("UPDATE native_community SET trade_policy=base_trade_policy, security_posture=base_security_posture WHERE id=?", community);
            jdbc.update("UPDATE native_settlement_site SET access_rule='INVITED' WHERE community_id=? AND access_rule='CLOSED'", community);
            record(community, day, "REOPENED", Map.of());
        }
        if (nextShortage >= SHORTAGE_MOVES && "SETTLED".equals(c.get("lifecycle"))) {
            jdbc.update("UPDATE native_community SET lifecycle='MOVING' WHERE id=?", community);
            record(community, day, "LEFT_TO_FIND_FOOD", Map.of("shortageDays", nextShortage));
        } else if (nextShortage == 0 && "MOVING".equals(c.get("lifecycle"))) {
            jdbc.update("UPDATE native_community SET lifecycle='SETTLED' WHERE id=?", community);
            record(community, day, "SETTLED_AGAIN", Map.of());
        }
    }

    /**
     * What one worker brings in a day, by season: the growing months enough to put by, the shoulders of the year
     * less, and deep winter a little, won through ice and from traps. Never nothing — a people that has lived on
     * this ground for generations is one that can get through its winters in an ordinary year — but never enough
     * in winter to feed everyone either, so the store they built in summer is what carries them. Dried food keeps
     * about six weeks, which is what makes a long winter a real danger rather than a formality.
     * Northern-hemisphere months, as the rest of the world's seasons are.
     */
    static int yieldPerWorker(Instant day) {
        int month = day.atZone(ZoneOffset.UTC).getMonthValue();
        return switch (month) {
            case 12, 1, 2 -> 1;
            case 3, 11 -> 2;
            default -> 3;
        };
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private void record(UUID community, Instant at, String kind, Map<String, Object> payload) {
        StringBuilder json = new StringBuilder("{");
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (json.length() > 1) json.append(',');
            json.append('"').append(e.getKey()).append("\":").append(e.getValue() instanceof Number ? e.getValue() : "\"" + e.getValue() + "\"");
        }
        json.append('}');
        jdbc.update("INSERT INTO native_event (community_id, occurred_at, event_kind, payload) VALUES (?,?,?,?::jsonb)",
            community, Timestamp.from(at), kind, json.toString());
    }
}
