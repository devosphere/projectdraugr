-- V133 — record NET as a fishing method (#36/#43).
--
-- fish() now reaches for a carried fishing/landing net, recording method_used='NET' on the catch — but the
-- aquatic_catch check constraint (V42) predated the net and allowed only BARE_HAND/SPEAR/TRAP/LINE, so a
-- net-caught fish failed to persist. Widen the constraint to include NET.
ALTER TABLE aquatic_catch DROP CONSTRAINT aquatic_catch_method_check;
ALTER TABLE aquatic_catch ADD CONSTRAINT aquatic_catch_method_check CHECK (method_used IN ('BARE_HAND','SPEAR','TRAP','LINE','NET'));
