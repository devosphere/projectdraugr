-- V72: Container access state — open / closed / sealed (M1 #67).
--
-- A container can be left OPEN (its mouth accessible), CLOSED (a lid or flap set over it), or SEALED (stoppered
-- or tied shut). Storing into or retrieving out of a container requires it to be OPEN — a closed or sealed one
-- must be opened first, which is a witnessed physical step, not a hidden rule. The state is additive: every
-- existing container defaults to OPEN, so nothing already in play changes. A later pass uses SEALED for weather
-- and spoilage protection; for now it gates access and is recorded in immutable object history like any change.

ALTER TABLE container_properties
    ADD COLUMN access_state VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    ADD CONSTRAINT container_properties_access_state_check CHECK (access_state IN ('OPEN', 'CLOSED', 'SEALED'));
