-- V433 — #108: stock that stray. "Escape through a broken gate" was a criterion with no mechanic under it: every
-- "escape" in the codebase was the Chronicle breaking contact with a predator (#126), and a tamed herd stayed put
-- forever whatever stood around it. So a pen kept stock in exactly as well as no pen at all, and a pen rotted to
-- nothing kept them in as well as a new one.
--
-- Now a herd left overnight with nothing SOUND around it (a barrier or enclosure at or above the "coming apart"
-- line the game already speaks with) loses one animal to wandering, at most once in a rest window. Fowl are
-- exempt — fowl go back to their roost at dusk, which is why people could keep them at all — and the last animal
-- of a herd stays, because a lone tame beast follows its keeper rather than a herd that is no longer there.
ALTER TABLE wildlife_bond ADD COLUMN IF NOT EXISTS last_strayed_at timestamptz;
COMMENT ON COLUMN wildlife_bond.last_strayed_at IS
    'When an animal last wandered off from this herd for want of a sound pen. Enforces a rest window so loose stock are thinned, not scattered in a night.';
