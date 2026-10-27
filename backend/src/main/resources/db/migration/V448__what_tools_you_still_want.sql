-- #37/#99 — the card for the reading of what a keeper still wants in the way of tools.
--
-- Three tool classes gate two hundred works, and tool_profile says exactly which items satisfy each:
--
--     CUTTING    198 works (157 material processes + the assembly stages)   19 items satisfy it
--     STRIKING    52 works                                                   9 items
--     AXE         28 works                                                   6 items
--
-- The gear reading answers what a keeper HAS. Nothing answered what they LACK — measured live, `what tools do
-- I need` reached nothing at all — so the way to find out was one refusal at a time, each costing the walk to
-- the work: "flesh a hide turns on a cutting edge, and there is none within reach."
--
-- The reading counts the works from the catalogue rather than from a number written in Java, so a new process
-- that wants a cutting edge raises the figure by itself. And it names WHAT WOULD DO, chosen the way a keeper
-- would choose: a tool that is simply picked up off the ground first (a field stone is a striking tool and
-- costs a stoop), then one the catalogue can make with no tool in hand (the knapped blades), and only then the
-- shortest name. Ordering by name alone had it answering "an iron axe would do" to somebody with no axe —
-- true, and useless.
--
-- EVERY NEW INTENT NEEDS A CARD, and ActivityImpactCardIntegrationTest skips locally, so a missing one costs an
-- hour and a half of CI. Casting over what you have and what the work wants is attention and nothing else.

INSERT INTO activity_impact (intent_key, footprint_kind, footprint_amount, drifts, only_with_fire,
                             labor_energy, labor_hygiene, capability_domain, duration_minutes, notes)
VALUES ('JUDGE_TOOLS', NULL, 0, FALSE, FALSE, 1, 0, 'ATTENTION', 3,
        'Perception alters nothing it touches. Casting over what you carry against what the work wants — a '
        || 'cutting edge, something to strike with, an axe — costs a little attention and marks nothing. No '
        || 'tool is made by asking after it.')
ON CONFLICT (intent_key) DO NOTHING;

DO $$
DECLARE n INT; e INT; d INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM activity_impact WHERE intent_key = 'JUDGE_TOOLS')
    THEN RAISE EXCEPTION 'V448: JUDGE_TOOLS has no activity_impact row'; END IF;

    SELECT COUNT(*) INTO n FROM activity_impact
     WHERE intent_key = 'JUDGE_TOOLS' AND (footprint_kind IS NOT NULL OR footprint_amount <> 0 OR drifts);
    IF n <> 0 THEN RAISE EXCEPTION 'V448: a reading must not mark the ground — no tool is made by asking'; END IF;
    SELECT labor_energy, duration_minutes INTO e, d FROM activity_impact WHERE intent_key = 'JUDGE_TOOLS';
    IF e < 1 OR d < 1 THEN RAISE EXCEPTION 'V448: the reading costs nothing and takes no time'; END IF;

    -- THE REGISTRY THE WHOLE READING STANDS ON. tool_profile is what makes a tool class a set of real items
    -- rather than a token (#93), and without it the reading could name nothing that would do.
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='tool_profile')
    THEN RAISE EXCEPTION 'V448: tool_profile is gone; a tool class would be a token again'; END IF;
    SELECT COUNT(DISTINCT tool_class) INTO n FROM tool_profile;
    IF n = 0 THEN RAISE EXCEPTION 'V448: tool_profile holds no classes at all'; END IF;

    -- EVERY CLASS THE WORKS ASK FOR MUST BE A CLASS SOME ITEM SATISFIES. A process gated on a class no item
    -- carries is unreachable work, and the reading would never mention it — a silent gap of exactly the kind
    -- this ticket exists to close. Asserted across both tables that gate on a tool.
    SELECT COUNT(*) INTO n FROM (
        SELECT DISTINCT mp.tool_class AS tc FROM material_process mp
         WHERE mp.review_state='VERIFIED' AND COALESCE(mp.tool_class,'') <> ''
        UNION
        SELECT DISTINCT s.tool_class FROM assembly_stage s WHERE COALESCE(s.tool_class,'') <> ''
    ) asked
     WHERE NOT EXISTS (SELECT 1 FROM tool_profile p WHERE p.tool_class = asked.tc);
    IF n > 0 THEN
        RAISE EXCEPTION 'V448: % tool class(es) are asked for by works and satisfied by no item', n;
    END IF;

    -- And something must be obtainable with nothing in hand, or the first-era keeper can never start: at least
    -- one tool item that is picked up off the ground or made with no tool at all.
    SELECT COUNT(*) INTO n FROM tool_profile p
     WHERE EXISTS (SELECT 1 FROM item_source s WHERE s.item_key=p.item_key AND s.source_kind IN ('CODE','MINERAL'))
        OR EXISTS (SELECT 1 FROM material_process m WHERE m.output_item_key=p.item_key
                     AND m.review_state='VERIFIED' AND COALESCE(m.tool_class,'') = '');
    IF n = 0 THEN RAISE EXCEPTION 'V448: no tool can be had without already owning one'; END IF;
END $$;
