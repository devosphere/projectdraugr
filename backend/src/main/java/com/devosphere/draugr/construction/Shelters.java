package com.devosphere.draugr.construction;

/**
 * What counts as an enclosing shelter, written once (#77/#219).
 *
 * <p>Five separate places in the Java ask the same question — the warmth the Body HUD grants, the recovery a rest
 * gives, whether sleep is deep or shallow, whether taking cover succeeds, and whether there is a roof a smoke vent
 * can be cut through — and four of them answered it by naming four project kinds literally. A Chronicle could
 * raise a pit house, a hide tent, a debris hut, a bark cabin or a snow shelter and get none of it.
 *
 * <p>The fifth was moved onto {@code is_shelter}, which turns out to be the wrong flag for this: thirty-four kinds
 * carry it and fourteen of them are a bark door, a reed door, a door hanging, a low stone wall, an earth berm, a
 * roofing frame, two screens, a smoke hood and three pieces of furniture. Those are all shelter-domain builds —
 * they belong to the shelter and wear like it — but a bark door lying on open grassland is not a roof over your
 * head. {@code encloses} (V280) is the narrower question these five actually mean, and this constant is where it
 * gets asked, so the next caller cannot drift away again.
 *
 * <p>The three legacy kinds that have no {@code construction_kind} row (the two huts and the log cabin, which are
 * assemblies) stay named explicitly, so nothing that sheltered before stops sheltering now.
 *
 * <p>Written against the alias {@code cp} for {@code construction_project}, which is what every caller uses.
 */
public final class Shelters {

    private Shelters() { }

    /**
     * True when a people has a house standing on the ground this Chronicle is on, and the Chronicle has leave to be
     * under it (#113): a guest with a night's welcome, or someone with a place among them. A roof is a roof, and
     * theirs keeps the rain off exactly as well as one you built — the difference is that it is not yours, and the
     * leave to be under it runs out.
     *
     * <p>The welcome is measured against the world clock, not the wall clock: a night granted in the world is a
     * night in the world, and comparing it to {@code now()} would have made every welcome run until the machine
     * caught up with the simulation.
     *
     * @param chronicleExpr SQL for the chronicle's id in the calling query (a bind '?' or a column)
     */
    public static String lentByAPeople(String chronicleExpr) {
        return "EXISTS (SELECT 1 FROM native_settlement_site s JOIN world_object house ON house.id=s.object_id " +
               "JOIN world_object guest ON guest.id=" + chronicleExpr + " " +
               "WHERE s.site_kind='VILLAGE' AND s.condition_percent > 0 AND house.lifecycle_state='ACTIVE' " +
               "AND house.current_location_id = guest.current_location_id " +
               "AND (EXISTS (SELECT 1 FROM native_membership m WHERE m.community_id=s.community_id AND m.chronicle_id=guest.id AND m.left_at IS NULL) " +
               "  OR EXISTS (SELECT 1 FROM native_guest_right g WHERE g.community_id=s.community_id AND g.chronicle_id=guest.id " +
               "                AND g.welcome_until > (SELECT simulated_at FROM simulation_clock WHERE id=1))))";
    }

    /** True for a build you can be inside. Requires {@code construction_project} to be in scope as {@code cp}. */
    public static final String ENCLOSING =
        "(cp.project_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') " +
        " OR EXISTS(SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.encloses))";
}
