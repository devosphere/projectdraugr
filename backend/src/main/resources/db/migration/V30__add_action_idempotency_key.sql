-- Optional client-supplied idempotency key so a duplicated action submission
-- (double-click that slips through, or a transport-level retry) resolves once.
ALTER TABLE chronicle_action ADD COLUMN idempotency_key UUID;
CREATE UNIQUE INDEX ux_chronicle_action_idempotency ON chronicle_action (idempotency_key) WHERE idempotency_key IS NOT NULL;
