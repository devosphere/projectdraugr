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

    /** True for a build you can be inside. Requires {@code construction_project} to be in scope as {@code cp}. */
    public static final String ENCLOSING =
        "(cp.project_kind IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') " +
        " OR EXISTS(SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.encloses))";
}
