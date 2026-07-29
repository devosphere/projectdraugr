# Database Monitoring

Project Draugr can run pgAdmin as an optional local-only dashboard. It does not modify Docker Desktop settings or expose PostgreSQL to the internet.

## Start pgAdmin

In PowerShell, from the repository root:

```powershell
$env:DRAUGR_PGADMIN_PASSWORD = Read-Host 'Choose a local pgAdmin password'
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d pgadmin
```

Open `http://localhost:5050` and sign in with `admin@draugr.app` and the password entered above.

Add one server connection:

| Field | Value |
| --- | --- |
| Name | Project Draugr (local) |
| Host name/address | `postgres` |
| Port | `5432` |
| Maintenance database | `draugr` |
| Username | `draugr` |
| Password | `draugr` |

The host is `postgres`, not `localhost`, because pgAdmin connects over the isolated Project Draugr Docker network.

## Useful read-only queries

```sql
-- Current persistent world objects by kind
SELECT object_type, COUNT(*)
FROM world_object
GROUP BY object_type
ORDER BY object_type;

-- Ecology created during World Genesis
SELECT site_category, site_kind, COUNT(*)
FROM ecology_site
GROUP BY site_category, site_kind
ORDER BY site_category, site_kind;

-- Immutable global history, newest first
SELECT occurred_at, event_type, aggregate_id, payload
FROM world_event
ORDER BY occurred_at DESC
LIMIT 100;

-- Chronicle status and archive
SELECT sequence_number, life_state, arrived_at, died_at, death_cause
FROM chronicle
ORDER BY sequence_number;

-- Chronicle-specific immutable history
SELECT chronicle_id, occurred_at, event_type, payload
FROM chronicle_event
ORDER BY occurred_at DESC;
```

Use the Query Tool for observation. Do not edit or delete rows manually: current state is authoritative and history tables reject mutation by design.
