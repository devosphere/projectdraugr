# World Genesis

World Genesis creates the canonical geography once, before any Chronicle arrives. The creator atlas represents a 280 × 200 km region in 10 km strategic cells; later simulation sub-chunks resolve this into local terrain. The seed is reproducibility metadata, not permission to regenerate reality.

Basic survival resources are intentionally redundant: every viable ecoregion must provide reachable water, combustible material, food or forage, and shelter materials. Rare metals, dangerous monster habitats, and exceptional ruins create expedition goals, but must never be the only source of basic survival.

The generated world contains terrain and biomes only in this first persisted slice. Preview maps include deterministic proposed markers for resource sites, wildlife ranges, monster lairs, and ancient ruins so the world can be reviewed as a whole. These markers become authoritative entities only in later Genesis phases. No living human or NPC civilization is created.

`POST /api/overseer/world/preview` is a local review operation. It creates a deterministic PNG without changing canonical state. After approval, `POST /api/overseer/world/genesis` can succeed exactly once. It records the root world and every generated chunk in PostgreSQL, and creates `world-exports/overseer-map.png`. Neither image is player-facing map data.
