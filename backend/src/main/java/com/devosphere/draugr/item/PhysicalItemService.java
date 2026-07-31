package com.devosphere.draugr.item;

import com.devosphere.draugr.ecology.ResourceEcologyService;
import com.devosphere.draugr.routing.ProcessMatcher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class PhysicalItemService {
    private final JdbcTemplate jdbc; private final ResourceEcologyService resources;
    private final ProcessMatcher matcher;
    public PhysicalItemService(JdbcTemplate jdbc, ResourceEcologyService resources, ProcessMatcher matcher) {
        this.jdbc = jdbc; this.resources = resources; this.matcher = matcher;
    }

    @Transactional(readOnly = true)
    public List<ItemView> carried() {
        UUID chronicle = activeChronicle();
        return jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT w.id, w.display_name, i.item_key, w.current_owner_id, ic.container_id FROM reachable r JOIN world_object w ON w.id=r.id JOIN item_instance i ON i.object_id=w.id LEFT JOIN item_containment ic ON ic.item_id=w.id ORDER BY w.display_name", (rs,row) -> new ItemView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getObject(5,UUID.class)), chronicle);
    }
    @Transactional(readOnly = true)
    public ItemState state() {
        UUID chronicle=activeChronicle();
        List<ItemView> carried=carried();
        List<EquippedView> equipped=jdbc.query("SELECT w.id,w.display_name,i.item_key,e.body_position,e.layer FROM equipment_attachment e JOIN world_object w ON w.id=e.item_id JOIN item_instance i ON i.object_id=w.id WHERE e.chronicle_id=? ORDER BY e.body_position,e.layer",(rs,row)->new EquippedView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),chronicle);
        return new ItemState(chronicle,carried,equipped,loadState(chronicle),containers(chronicle));
    }
    @Transactional
    public int gatherPlantFiber(UUID chronicle, UUID location, Instant occurredAt) {
        String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,location);
        if ("OCEAN".equals(biome) || "MOUNTAIN".equals(biome)) return 0; // No suitable fiber here; resolves as a graceful empty-handed attempt.
        int desired=Math.min("WETLAND".equals(biome)?3:2, capacityHeadroomUnits(chronicle,"plant_fiber"));
        if(desired<=0) return 0; // The Chronicle cannot carry any more; a graceful empty-handed attempt.
        int count=resources.take(location,"plant_fiber",desired,occurredAt);
        for(int i=0;i<count;i++){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Plant fiber bundle',?)",id,chronicle);jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'plant_fiber','SOUND')",id);jdbc.update("INSERT INTO object_transition (object_id,transition_type,payload) VALUES (?,'GATHERED',jsonb_build_object('biome',?))",id,biome);}
        assertCarryCapacity(chronicle);
        return count;
    }
    @Transactional
    public int gatherFieldStones(UUID chronicle, UUID location, Instant occurredAt) {
        String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,location);
        if ("OCEAN".equals(biome)) return 0; // No loose stone here; resolves as a graceful empty-handed attempt.
        int desired=Math.min("MOUNTAIN".equals(biome) || "HIGHLAND".equals(biome) ? 3 : 2, capacityHeadroomUnits(chronicle,"field_stone"));
        if(desired<=0) return 0; // The Chronicle cannot carry any more; a graceful empty-handed attempt.
        int count=resources.take(location,"field_stone",desired,occurredAt);
        for(int i=0;i<count;i++){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Field stone',?)",id,chronicle);jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'field_stone','SOUND')",id);jdbc.update("INSERT INTO object_transition (object_id,transition_type,payload) VALUES (?,'GATHERED',jsonb_build_object('biome',?))",id,biome);}
        assertCarryCapacity(chronicle);
        return count;
    }
    @Transactional
    public int gatherWildBerries(UUID chronicle, UUID location, Instant occurredAt) {
        String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,location);
        if ("MOUNTAIN".equals(biome) || "OCEAN".equals(biome)) return 0; // No edible growth here; resolves as a graceful empty-handed attempt.
        int desired=Math.min("WETLAND".equals(biome)?3:2, capacityHeadroomUnits(chronicle,"wild_berries"));
        if(desired<=0) return 0; // The Chronicle cannot carry any more; a graceful empty-handed attempt.
        int count=resources.take(location,"wild_berries",desired,occurredAt);
        for(int i=0;i<count;i++){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Wild berries',?)",id,chronicle);jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'wild_berries','SOUND')",id);jdbc.update("INSERT INTO object_transition (object_id,transition_type,payload) VALUES (?,'GATHERED',jsonb_build_object('biome',?))",id,biome);} assertCarryCapacity(chronicle); return count;
    }
    @Transactional
    public int gatherDryBranches(UUID chronicle, UUID location, Instant occurredAt) {
        String biome=jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?",String.class,location);
        if ("OCEAN".equals(biome)) return 0; // No branches here; resolves as a graceful empty-handed attempt.
        int desired=Math.min("MOUNTAIN".equals(biome)?1:2, capacityHeadroomUnits(chronicle,"dry_branch"));
        if(desired<=0) return 0; // The Chronicle cannot carry any more; a graceful empty-handed attempt.
        int count=resources.take(location,"dry_branch",desired,occurredAt);
        for(int i=0;i<count;i++){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Dry branch',?)",id,chronicle);jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'dry_branch','SOUND')",id);jdbc.update("INSERT INTO object_transition (object_id,transition_type,payload) VALUES (?,'GATHERED','{}'::jsonb)",id);} assertCarryCapacity(chronicle); return count;
    }
    @Transactional
    public boolean consumeOne(UUID chronicle, String itemKey, Instant occurredAt) {
        UUID item=jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key=? ORDER BY r.id FOR UPDATE LIMIT 1",rs->rs.next()?rs.getObject(1,UUID.class):null,chronicle,itemKey);
        if(item==null)return false; retire(item,occurredAt,"CONSUMED",itemKey); return true;
    }
    @Transactional
    public UUID createCarriedItem(UUID chronicle, String itemKey, String displayName, Instant occurredAt, String transitionType) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,?,jsonb_build_object('itemKey',?))", id, Timestamp.from(occurredAt), transitionType, itemKey);
        assertCarryCapacity(chronicle);
        return id;
    }
    /** The id of one reachable item of a kind (carried or in a carried container), or null. */
    @Transactional(readOnly = true)
    public UUID findReachable(UUID chronicle, String itemKey) {
        return jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key=? ORDER BY r.id LIMIT 1", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle, itemKey);
    }
    /** Strip a workable sheet of bark from a tree — a writing surface. Wooded terrain only. */
    @Transactional
    public int stripBark(UUID chronicle, UUID location, Instant occurredAt) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if (!"TEMPERATE_FOREST".equals(biome)) return 0; // No trees with workable bark here.
        if (capacityHeadroomUnits(chronicle, "bark_sheet") <= 0) return 0;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Bark sheet',?)", id, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'bark_sheet','SOUND')", id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('material','bark_sheet'))", id, Timestamp.from(occurredAt));
        return 1;
    }
    /** Dig clay from wet earth. Yield scales with biome: CLAY_DEPOSIT > RIVER_BANK > WETLAND. Dry biomes yield nothing. */
    @Transactional
    public int gatherClay(UUID chronicle, UUID location, Instant occurredAt) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        int yield = switch (biome != null ? biome : "") {
            case "CLAY_DEPOSIT" -> 3;
            case "RIVER_BANK"   -> 2;
            case "WETLAND"      -> 1;
            default -> 0;
        };
        if (yield == 0) return 0;
        int desired = Math.min(yield, capacityHeadroomUnits(chronicle, "clay_lump"));
        if (desired <= 0) return 0;
        for (int i = 0; i < desired; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Clay lump',?)", id, chronicle);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'clay_lump','SOUND')", id);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('biome',?))", id, Timestamp.from(occurredAt), biome);
        }
        assertCarryCapacity(chronicle);
        return desired;
    }
    /** Split a flat stone slab from rock — a heavy, permanent writing surface. Stony highland terrain only. */
    @Transactional
    public int gatherStoneSlab(UUID chronicle, UUID location, Instant occurredAt) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        if (!"MOUNTAIN".equals(biome) && !"HIGHLAND".equals(biome)) return 0; // No workable rock face here.
        if (capacityHeadroomUnits(chronicle, "stone_slab") <= 0) return 0;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Stone slab',?)", id, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'stone_slab','SOUND')", id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('biome',?))", id, Timestamp.from(occurredAt), biome);
        assertCarryCapacity(chronicle);
        return 1;
    }
    /** Take a piece of charcoal from a spent fire — a writing implement. Requires a fire that was lit here and has since burned out. */
    @Transactional
    public boolean makeCharcoal(UUID chronicle, UUID location, Instant occurredAt) {
        // Charcoal comes from wood that has actually burned. A fire pit that was
        // built but never lit is just a ring of cold stone. Require a fire_state
        // row (the pit was lit at least once) that is no longer burning.
        Integer spent = jdbc.queryForObject("SELECT COUNT(*) FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND cp.project_kind='STONE_FIRE_PIT' AND cp.state='COMPLETED' AND fs.active=false", Integer.class, location);
        if (spent == null || spent == 0) return false;
        if (capacityHeadroomUnits(chronicle, "charcoal") <= 0) return false;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Charcoal',?)", id, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'charcoal','SOUND')", id);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'MADE_CHARCOAL','{}'::jsonb)", id, Timestamp.from(occurredAt));
        return true;
    }
    /**
     * Gather a plant from the flora_drop table — mushroom, herb, berry, root, or
     * any non-tree flora. The action text is used to infer the target species; if
     * unrecognised, the first matching flora in the chunk's biome is used.
     * Season gates and tool requirements are enforced. Returns [outcome, narration].
     */
    @Transactional
    public String[] gatherPlant(UUID chronicle, UUID location, String actionText, Instant occurredAt) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        String lower = actionText.toLowerCase(java.util.Locale.ROOT);
        String season = seasonOf(occurredAt);

        // Find a flora in this biome that the action text names (or any available)
        java.util.List<java.util.Map<String,Object>> candidates = jdbc.queryForList(
            "SELECT fd.flora_key, fd.organism_type, fd.tool_required, fd.is_poisonous " +
            "FROM flora_definition fd " +
            "JOIN chunk_flora cf ON cf.flora_key=fd.flora_key " +
            "WHERE cf.chunk_id=? AND cf.quantity > 0 AND fd.organism_type <> 'TREE' " +
            "ORDER BY fd.flora_key", location);

        if (candidates.isEmpty()) {
            // No chunk_flora rows — fall back to biome affinity check
            candidates = jdbc.queryForList(
                "SELECT flora_key, organism_type, tool_required, is_poisonous " +
                "FROM flora_definition " +
                "WHERE organism_type <> 'TREE' AND biome_affinity ILIKE ? " +
                "ORDER BY flora_key", "%" + biome + "%");
        }
        if (candidates.isEmpty()) return new String[]{"FAILED", "You search through the growth, but find nothing here worth taking."};

        // Prefer species the action text names
        java.util.Map<String,Object> target = candidates.stream()
            .filter(c -> lower.contains(((String)c.get("flora_key")).replace("_"," ")))
            .findFirst()
            .orElse(candidates.get(0));

        String floraKey = (String) target.get("flora_key");
        String toolRequired = (String) target.get("tool_required");

        // Tool gate — only KNIFE_CLASS tools apply here (no flora needs axe bare-hand)
        if (toolRequired != null && toolRequired.contains("KNIFE") && !hasCuttingTool(chronicle)) {
            return new String[]{"FAILED", "You reach for the plant, but it needs cutting and you carry no blade."};
        }

        // Get drops for this flora, filtered by season
        java.util.List<java.util.Map<String,Object>> drops = jdbc.queryForList(
            "SELECT item_key, yield_min, yield_max, season FROM flora_drop " +
            "WHERE flora_key=? AND (season IS NULL OR season=? OR ? IS NULL) " +
            "ORDER BY item_key", floraKey, season, season);

        if (drops.isEmpty()) {
            return new String[]{"FAILED", "You find the plant but nothing here is ready to take — wrong season or nothing ripe."};
        }

        // Pick first available drop (simplest — can expand to multi-drop later)
        java.util.Map<String,Object> drop = drops.get(0);
        String itemKey = (String) drop.get("item_key");
        int yieldMin = ((Number) drop.get("yield_min")).intValue();
        int yieldMax = ((Number) drop.get("yield_max")).intValue();
        int yield = yieldMin + (yieldMax > yieldMin ? (int)(Math.random() * (yieldMax - yieldMin + 1)) : 0);
        int available = capacityHeadroomUnits(chronicle, itemKey);
        int count = Math.min(yield, Math.max(1, available));
        if (available <= 0) return new String[]{"FAILED", "You cannot carry any more of what you find here."};

        // Look up display name from item_definition
        String displayName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);

        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, chronicle);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('floraKey',?,'biome',?))", id, Timestamp.from(occurredAt), floraKey, biome);
        }
        // Deplete chunk_flora if present
        jdbc.update("UPDATE chunk_flora SET quantity=GREATEST(0,quantity-?), last_harvested_at=? WHERE chunk_id=? AND flora_key=?", count, Timestamp.from(occurredAt), location, floraKey);
        assertCarryCapacity(chronicle);
        String plantName = floraKey.replace("_", " ");
        return new String[]{"SUCCEEDED", "You gather " + (count == 1 ? "a" : count + "") + " " + displayName.toLowerCase() + " from the " + plantName + " growing here."};
    }

    /**
     * Fell a tree in the current chunk — requires an axe-class tool equipped or
     * carried. Yields logs and any secondary drops from flora_drop. Returns [outcome, narration].
     */
    @Transactional
    public String[] fellTree(UUID chronicle, UUID location, Instant occurredAt) {
        // Axe-class tool check: stone_axe, stone_hatchet, or any future axe item
        boolean hasAxe = Boolean.TRUE.equals(jdbc.queryForObject(
            "WITH RECURSIVE reachable(id) AS (" +
            "  SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'" +
            "  UNION ALL SELECT ic.item_id FROM item_containment ic" +
            "  JOIN reachable r ON r.id=ic.container_id" +
            "  JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE')" +
            "SELECT EXISTS(SELECT 1 FROM reachable r JOIN item_instance i ON i.object_id=r.id " +
            "WHERE i.item_key IN ('stone_axe','stone_hatchet','iron_axe','hand_axe'))",
            Boolean.class, chronicle));
        if (!hasAxe) return new String[]{"FAILED", "You set your hands against the trunk. Without an axe, you cannot fell a tree."};

        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        // Check that there is a tree here via chunk_flora
        java.util.Map<String,Object> tree = jdbc.query(
            "SELECT cf.flora_key, fd.organism_type FROM chunk_flora cf " +
            "JOIN flora_definition fd ON fd.flora_key=cf.flora_key " +
            "WHERE cf.chunk_id=? AND fd.organism_type='TREE' AND cf.quantity>0 LIMIT 1",
            rs -> rs.next() ? java.util.Map.of("flora_key", rs.getString(1), "organism_type", rs.getString(2)) : null,
            location);

        if (tree == null) {
            // Fall back: biome has trees by nature even without chunk_flora rows
            String treeKey = switch (biome != null ? biome : "") {
                case "FOREST", "TEMPERATE_FOREST" -> "oak";
                case "MOUNTAIN" -> "spruce";
                case "HIGHLAND" -> "pine";
                case "WETLAND", "RIVERBANK" -> "willow";
                default -> null;
            };
            if (treeKey == null) return new String[]{"FAILED", "There are no trees here to fell."};
            tree = java.util.Map.of("flora_key", treeKey, "organism_type", "TREE");
        }

        String floraKey = (String) tree.get("flora_key");
        // Get log drop for this tree
        java.util.List<java.util.Map<String,Object>> drops = jdbc.queryForList(
            "SELECT item_key, yield_min, yield_max FROM flora_drop WHERE flora_key=? AND tool_condition='AXE_CLASS'", floraKey);
        if (drops.isEmpty()) return new String[]{"FAILED", "The tree stands but offers nothing your axe can shape."};

        String logKey = (String) drops.get(0).get("item_key");
        int logYield = ((Number)drops.get(0).get("yield_min")).intValue();
        String logName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, logKey);

        int available = capacityHeadroomUnits(chronicle, logKey);
        int count = Math.min(logYield, Math.max(1, available));
        if (available <= 0) return new String[]{"FAILED", "You cannot carry the logs — your hands and back are full."};

        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, logName, chronicle);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, logKey);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'FELLED',jsonb_build_object('floraKey',?,'biome',?))", id, Timestamp.from(occurredAt), floraKey, biome);
        }
        jdbc.update("UPDATE chunk_flora SET quantity=GREATEST(0,quantity-1), last_harvested_at=? WHERE chunk_id=? AND flora_key=?", Timestamp.from(occurredAt), location, floraKey);
        assertCarryCapacity(chronicle);
        String treeName = floraKey.replace("_", " ");
        return new String[]{"SUCCEEDED", "The " + treeName + " comes down with a crack that carries across the ground. You take up " + count + " " + logName.toLowerCase() + (count > 1 ? "s" : "") + " from what lies."};
    }

    @Transactional(readOnly = true)
    public boolean hasAtLeast(UUID chronicle, String itemKey, int required) {
        Integer count = jdbc.queryForObject("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key=?", Integer.class, chronicle, itemKey);
        return count != null && count >= required;
    }

    /**
     * Run a material process from the declarative table (V52): splitting planks,
     * dressing stone, twisting cordage, tanning hide, rendering pitch, firing a pot.
     *
     * <p>The whole chain is data. Nothing here knows what a plank is — it reads which
     * process the action names, checks that the inputs, tool class, fire and water it
     * declares are actually present, then consumes and produces. Adding a material
     * chain is a migration, not a code change, which is the point: every gap in that
     * table is a moment the simulation cannot resolve on its own and has to spend an
     * AI call instead.
     *
     * @return [outcome, narration]
     */
    @Transactional
    public String[] runProcess(UUID chronicle, UUID location, String actionText, Instant at) {
        // A process may run only when the text agrees with it on the kind of work, the
        // verb, and the material — see ProcessMatcher, which holds the rule and the
        // reasoning. Keyword alone was how "split the fish" reached split_planks, and
        // a wrong match does not throw: it quietly does the wrong thing to a
        // chronicle's inventory. The narration says nothing about why nothing matched;
        // the world simply does not yet know how to do it.
        String key = matcher.match(actionText);
        if (key == null) return new String[]{"FAILED", "You turn the material over without settling on what to do with it."};
        java.util.Map<String,Object> match = jdbc.queryForMap(
            "SELECT process_key, display_name, output_item_key, output_min, output_max, tool_class, " +
            "requires_fire, requires_water, narration FROM material_process WHERE process_key=?", key);

        String toolClass = (String) match.get("tool_class");
        if (toolClass != null) {
            boolean ok = switch (toolClass) {
                case "CUTTING"  -> hasCuttingTool(chronicle);
                case "STRIKING" -> hasAtLeast(chronicle,"stone_hammer",1) || hasAtLeast(chronicle,"primitive_pickaxe",1) || hasAtLeast(chronicle,"field_stone",1);
                case "AXE"      -> hasAtLeast(chronicle,"stone_axe",1) || hasAtLeast(chronicle,"stone_hatchet",1);
                default -> true; };
            if (!ok) return new String[]{"FAILED", "The work needs a tool you are not carrying."};
        }
        if (Boolean.TRUE.equals(match.get("requires_fire"))) {
            Boolean fire = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id WHERE w.current_location_id=? AND fs.active=true)", Boolean.class, location);
            if (!Boolean.TRUE.equals(fire)) return new String[]{"FAILED", "It needs heat, and there is no fire burning here."};
        }
        if (Boolean.TRUE.equals(match.get("requires_water"))) {
            String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
            if (!"WETLAND".equals(biome)) return new String[]{"FAILED", "It needs water, and there is none here to work with."};
        }

        // Fixed inputs, then each either/or group.
        java.util.List<java.util.Map<String,Object>> fixed = jdbc.queryForList(
            "SELECT item_key, quantity FROM material_process_input WHERE process_key=?", key);
        for (java.util.Map<String,Object> in : fixed)
            if (!hasAtLeast(chronicle, (String) in.get("item_key"), ((Number) in.get("quantity")).intValue()))
                return new String[]{"FAILED", "You have not got enough to hand for that."};

        java.util.List<String> groups = jdbc.queryForList(
            "SELECT DISTINCT group_name FROM material_process_input_group WHERE process_key=?", String.class, key);
        java.util.Map<String,String> chosen = new java.util.LinkedHashMap<>();
        for (String g : groups) {
            String pick = null;
            for (java.util.Map<String,Object> o : jdbc.queryForList(
                    "SELECT item_key, quantity FROM material_process_input_group WHERE process_key=? AND group_name=?", key, g))
                if (hasAtLeast(chronicle, (String) o.get("item_key"), ((Number) o.get("quantity")).intValue())) { pick = (String) o.get("item_key"); break; }
            if (pick == null) return new String[]{"FAILED", "You have nothing suitable to work from."};
            chosen.put(g, pick);
        }

        String outKey = (String) match.get("output_item_key");
        if (capacityHeadroomUnits(chronicle, outKey) <= 0)
            return new String[]{"FAILED", "You could do the work, but you could not carry what it would make."};

        for (java.util.Map<String,Object> in : fixed)
            for (int i = 0; i < ((Number) in.get("quantity")).intValue(); i++) consumeOne(chronicle, (String) in.get("item_key"), at);
        for (java.util.Map.Entry<String,String> e : chosen.entrySet()) {
            Integer q = jdbc.queryForObject("SELECT quantity FROM material_process_input_group WHERE process_key=? AND group_name=? AND item_key=?", Integer.class, key, e.getKey(), e.getValue());
            for (int i = 0; i < (q == null ? 1 : q); i++) consumeOne(chronicle, e.getValue(), at);
        }

        int lo = ((Number) match.get("output_min")).intValue(), hi = ((Number) match.get("output_max")).intValue();
        int made = Math.min(lo + (hi > lo ? (int)(Math.random()*(hi-lo+1)) : 0), Math.max(1, capacityHeadroomUnits(chronicle, outKey)));
        String outName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, outKey);
        for (int i = 0; i < made; i++) createCarriedItem(chronicle, outKey, outName, at, "PROCESSED");
        assertCarryCapacity(chronicle);
        return new String[]{"SUCCEEDED", (String) match.get("narration")};
    }

    /**
     * Search the ground and exposed rock for a mineral (V50). What can be found is
     * decided by the geology of the biome, and whether it IS found by the mineral's
     * own rarity — searching stone for a flint nodule is patient work that often
     * comes to nothing. A named mineral is searched for specifically; an unnamed
     * search turns up whatever this ground most readily offers.
     *
     * @return [outcome, narration]
     */
    @Transactional
    public String[] gatherMineral(UUID chronicle, UUID location, String actionText, Instant occurredAt) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        String v = actionText.toLowerCase(java.util.Locale.ROOT);

        java.util.List<java.util.Map<String,Object>> here = jdbc.queryForList(
            "SELECT mineral_key, display_name, rarity, tool_required, yield_min, yield_max FROM mineral_definition " +
            "WHERE biome_affinity ILIKE ? ORDER BY rarity DESC", "%" + biome + "%");
        if (here.isEmpty())
            return new String[]{"FAILED", "You turn over what stone there is. This ground has nothing in it but dirt."};

        java.util.Map<String,Object> target = here.stream()
            .filter(m -> v.contains(((String)m.get("mineral_key")).replace('_',' ')) || v.contains(((String)m.get("display_name")).toLowerCase()))
            .findFirst().orElse(null);
        boolean named = target != null;
        if (target == null) target = here.get(0);

        String key = (String) target.get("mineral_key");
        String name = (String) target.get("display_name");
        String tool = (String) target.get("tool_required");
        if (tool != null && !hasCuttingTool(chronicle) && !hasAtLeast(chronicle,"stone_hammer",1) && !hasAtLeast(chronicle,"primitive_pickaxe",1))
            return new String[]{"FAILED", "The " + name.toLowerCase() + " is locked in the rock, and you have nothing to break it free with."};

        // Searching for one specific mineral is harder than taking what is plainly there.
        double chance = ((Number) target.get("rarity")).doubleValue() * (named ? 0.75 : 1.0);
        if (Math.random() > chance)
            return new String[]{"FAILED", named
                ? "You work along the rock looking for " + name.toLowerCase() + ", turning over what looks promising. None of it is."
                : "You search the stone for a long while and come away with nothing worth carrying."};

        int lo = ((Number)target.get("yield_min")).intValue(), hi = ((Number)target.get("yield_max")).intValue();
        int want = lo + (hi > lo ? (int)(Math.random()*(hi-lo+1)) : 0);
        int room = capacityHeadroomUnits(chronicle, key);
        int take = Math.min(want, room);
        if (take <= 0) return new String[]{"FAILED", "You find what you were after and cannot carry another thing."};
        for (int i = 0; i < take; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, name, chronicle);
            jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, key);
            jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('mineral',?,'biome',?))", id, Timestamp.from(occurredAt), key, biome);
        }
        assertCarryCapacity(chronicle);
        return new String[]{"SUCCEEDED", "You work it loose and turn it over in your hand: " + name.toLowerCase() + (take > 1 ? ", and more of it nearby." : ".")};
    }

    /**
     * Sew or weave a garment. Hide and fur come off animals the chronicle killed;
     * a woven tunic comes off the plants they gathered. Cutting work needs a blade.
     * The garment is created worn-ready but not equipped — putting it on is the
     * player's own decision, through EQUIP.
     *
     * @return [outcome, narration]
     */
    @Transactional
    public String[] craftGarment(UUID chronicle, String actionText, Instant occurredAt) {
        String v = actionText.toLowerCase(java.util.Locale.ROOT);
        record Pattern(String itemKey, String name, String hideKind, int hides, int fiber, boolean needsBlade) { }
        Pattern p =
            v.contains("cloak") || v.contains("fur")       ? new Pattern("fur_cloak",    "Fur cloak",     "pelt", 2, 1, true)
          : v.contains("legging") || v.contains("trouser") ? new Pattern("hide_leggings","Hide leggings", "hide", 1, 1, true)
          : v.contains("boot") || v.contains("shoe")       ? new Pattern("hide_boots",   "Hide boots",    "hide", 1, 1, true)
          : v.contains("tunic") || v.contains("woven")     ? new Pattern("fiber_tunic",  "Woven tunic",   null,   0, 6, false)
          :                                                  new Pattern("hide_coat",    "Hide coat",     "hide", 2, 1, true);

        if (p.needsBlade() && !hasCuttingTool(chronicle))
            return new String[]{"FAILED", "You lay the material out and reach for something to cut it with. You have no blade."};

        // Pelts are warmer than plain hide, so a cloak asks for them specifically;
        // anything else takes whatever skin is to hand.
        java.util.List<String> hideKeys = "pelt".equals(p.hideKind())
            ? java.util.List.of("wolf_pelt","bear_pelt","fox_pelt","lynx_pelt","rabbit_pelt","dire_wolf_pelt")
            : java.util.List.of("animal_hide","deer_hide","boar_hide","troll_hide","wolf_pelt","bear_pelt","fox_pelt","lynx_pelt","rabbit_pelt");

        int haveHides = 0;
        for (String k : hideKeys) { Integer n = jdbc.queryForObject(
            "WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object n ON n.id=ic.item_id WHERE n.lifecycle_state='ACTIVE') SELECT COUNT(*) FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key=?",
            Integer.class, chronicle, k); haveHides += n == null ? 0 : n; }

        if (haveHides < p.hides())
            return new String[]{"FAILED", p.hides() == 0 ? "You have nothing to work with." :
                "You spread out what skins you have and turn them over. There is not enough here to make " + p.name().toLowerCase() + " that would cover anything."};
        if (!hasAtLeast(chronicle, "plant_fiber", p.fiber()) && !hasAtLeast(chronicle, "animal_sinew", p.fiber()) && !hasAtLeast(chronicle, "fiber_cordage", p.fiber()))
            return new String[]{"FAILED", "The pieces sit together well enough, but you have nothing to stitch them with."};

        int taken = 0;
        for (String k : hideKeys) { while (taken < p.hides() && consumeOne(chronicle, k, occurredAt)) taken++; if (taken >= p.hides()) break; }
        for (int i = 0; i < p.fiber(); i++)
            if (!consumeOne(chronicle,"animal_sinew",occurredAt) && !consumeOne(chronicle,"fiber_cordage",occurredAt)) consumeOne(chronicle,"plant_fiber",occurredAt);

        createCarriedItem(chronicle, p.itemKey(), p.name(), occurredAt, "CRAFTED");
        return new String[]{"SUCCEEDED", "You work the material to shape and stitch it closed. The " + p.name().toLowerCase() + " is finished, and it is warm in the hand."};
    }

    /** The outcome of an insect harvest: what happened, how it read, and any hazard the body took. */
    public record InsectHarvest(String outcome, String narration, int hazardSeverity, String hazardKind) { }

    /**
     * Raid a hive or nest for its products — honey and wax from bees, venom from
     * hornets. Bee stings are suppressed when the raider works smoke (an active
     * fire in the chunk and smoke described in the action); hornets sting
     * regardless. The colony kind is inferred from the biome when no colony
     * instance is placed here, mirroring flora gathering.
     */
    @Transactional
    public InsectHarvest raidHive(UUID chronicle, UUID location, String actionText, Instant occurredAt) {
        return harvestColony(chronicle, location, actionText, occurredAt, "RAID_HIVE");
    }

    /**
     * Collect insects by hand — silk cocoons, ant chitin, edible grasshoppers and
     * crickets, earthworm bait, spider silk. Some colonies bite or bear venom; the
     * hazard is applied by the caller from the returned severity and kind.
     */
    @Transactional
    public InsectHarvest collectInsects(UUID chronicle, UUID location, String actionText, Instant occurredAt) {
        return harvestColony(chronicle, location, actionText, occurredAt, "COLLECT_INSECTS");
    }

    private InsectHarvest harvestColony(UUID chronicle, UUID location, String actionText, Instant occurredAt, String intent) {
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, location);
        String lower = actionText.toLowerCase(java.util.Locale.ROOT);
        String season = seasonOf(occurredAt);

        // Candidate colony kinds for this intent, present in this biome and season.
        java.util.List<java.util.Map<String,Object>> kinds = jdbc.queryForList(
            "SELECT ck.colony_kind, ck.hazard_kind, ck.hazard_min, ck.hazard_max, ck.smoke_suppresses " +
            "FROM insect_colony_kind ck " +
            "WHERE ck.harvest_intent=? AND ck.biome_affinity ILIKE ? " +
            "AND (ck.season_active='ALL' OR ck.season_active ILIKE ?) " +
            "ORDER BY ck.colony_kind", intent, "%" + biome + "%", "%" + season + "%");
        if (kinds.isEmpty()) {
            return new InsectHarvest("FAILED", intent.equals("RAID_HIVE")
                ? "You search for a hive or nest to raid, but find none here to work."
                : "You turn over the ground and growth for insects, but find nothing worth taking here.", 0, null);
        }

        // Prefer a colony the action text names; otherwise the first available.
        java.util.Map<String,Object> kind = kinds.stream()
            .filter(k -> lower.contains(((String)k.get("colony_kind")).replace("_"," ").replace(" colony","").replace(" swarm","").replace(" patch","").replace(" den","").replace(" nest","").replace(" hive","")))
            .findFirst().orElse(kinds.get(0));
        String colonyKind = (String) kind.get("colony_kind");
        String hazardKind = (String) kind.get("hazard_kind");
        int hazardMin = ((Number) kind.get("hazard_min")).intValue();
        int hazardMax = ((Number) kind.get("hazard_max")).intValue();
        boolean smokeSuppresses = Boolean.TRUE.equals(kind.get("smoke_suppresses"));

        // Smoke is real only when there is an active fire in the chunk and the
        // action actually describes using its smoke.
        boolean describesSmoke = lower.contains("smoke") || lower.contains("smok") || lower.contains("smoulder") || lower.contains("smolder");
        boolean activeFire = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN fire_state fs ON fs.construction_id=cp.object_id WHERE w.current_location_id=? AND fs.active=true)",
            Boolean.class, location));
        boolean smokeWorking = describesSmoke && activeFire;

        int hazardSeverity = 0;
        if (hazardKind != null && hazardMax > 0) {
            boolean suppressed = smokeSuppresses && smokeWorking;
            if (!suppressed) hazardSeverity = hazardMin + (hazardMax > hazardMin ? (int)(Math.random() * (hazardMax - hazardMin + 1)) : 0);
        }

        // Yield each product by its rarity roll, respecting carry capacity.
        java.util.List<java.util.Map<String,Object>> products = jdbc.queryForList(
            "SELECT item_key, yield_min, yield_max, rarity FROM insect_colony_product WHERE colony_kind=? ORDER BY rarity DESC", colonyKind);
        int totalTaken = 0; String firstItemName = null;
        for (java.util.Map<String,Object> p : products) {
            double rarity = ((Number) p.get("rarity")).doubleValue();
            if (Math.random() > rarity) continue;
            String itemKey = (String) p.get("item_key");
            int ymin = ((Number) p.get("yield_min")).intValue();
            int ymax = ((Number) p.get("yield_max")).intValue();
            int want = ymin + (ymax > ymin ? (int)(Math.random() * (ymax - ymin + 1)) : 0);
            int room = capacityHeadroomUnits(chronicle, itemKey);
            int take = Math.min(want, room);
            if (take <= 0) continue;
            String displayName = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
            if (firstItemName == null) firstItemName = displayName;
            for (int i = 0; i < take; i++) {
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, displayName, chronicle);
                jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
                jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'GATHERED',jsonb_build_object('colonyKind',?,'biome',?))", id, Timestamp.from(occurredAt), colonyKind, biome);
            }
            totalTaken += take;
        }
        // Record disturbance on any placed instance of this colony in the chunk.
        jdbc.update("UPDATE insect_colony SET last_disturbed_at=?, health=GREATEST(0,health-15) WHERE chunk_id=? AND colony_kind=?", Timestamp.from(occurredAt), location, colonyKind);
        if (totalTaken > 0) assertCarryCapacity(chronicle);

        String name = colonyKind.replace("_", " ");
        String narration;
        if (totalTaken == 0) {
            narration = intent.equals("RAID_HIVE")
                ? "You break into the " + name + ", but it yields nothing you can carry away this time."
                : "You work at the " + name + " for a while, but come away empty-handed.";
        } else if (hazardSeverity > 0) {
            narration = "You take " + firstItemName.toLowerCase() + " from the " + name + ", and the colony makes you pay for it before you withdraw.";
        } else {
            narration = "You take " + firstItemName.toLowerCase() + " from the " + name + ", working steadily until you have what you came for.";
        }
        String outcome = totalTaken > 0 ? "SUCCEEDED" : "FAILED";
        return new InsectHarvest(outcome, narration, hazardSeverity, hazardKind);
    }

    @Transactional
    public ItemView craftBasket() {
        UUID chronicle=activeChronicle();
        List<UUID> fiber=jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT r.id FROM reachable r JOIN item_instance i ON i.object_id=r.id WHERE i.item_key='plant_fiber' ORDER BY r.id FOR UPDATE", (rs,row)->rs.getObject(1,UUID.class), chronicle);
        if(fiber.size()<8) throw new IllegalStateException("Crafting a woven basket requires eight physical plant-fiber bundles carried by the Chronicle.");
        Instant now=Instant.now(); UUID basket=UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Woven basket',?)",basket,chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'woven_basket','SOUND')",basket);
        jdbc.update("INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml) VALUES (?,12000,18000)",basket);
        for(UUID material:fiber.subList(0,8)) { retire(material,now,"CONSUMED_FOR_CRAFTING","plant_fiber"); }
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CRAFTED',jsonb_build_object('recipe','woven_basket'))",basket,Timestamp.from(now));
        // Equip to BACK immediately — a basket is worn, not clutched. Falls back to carried if BACK is occupied.
        boolean backFree = jdbc.queryForObject("SELECT COUNT(*)=0 FROM equipment_attachment WHERE chronicle_id=? AND body_position='BACK'", Boolean.class, chronicle);
        if (Boolean.TRUE.equals(backFree)) { jdbc.update("INSERT INTO equipment_attachment (item_id,chronicle_id,body_position,layer) VALUES (?,?,'BACK','CARRIED')",basket,chronicle); jdbc.update("UPDATE world_object SET current_owner_id=? WHERE id=?",chronicle,basket); }
        assertCarryCapacity(chronicle);
        return new ItemView(basket,"Woven basket","woven_basket",chronicle,null);
    }
    @Transactional public ItemView craftPrimitiveSpear(Instant at) { UUID chronicle=activeChronicle(); if(!hasAtLeast(chronicle,"dry_branch",1)||!hasAtLeast(chronicle,"field_stone",1)||!hasAtLeast(chronicle,"plant_fiber",1))throw new IllegalStateException("Insufficient physical material."); if(!consumeOne(chronicle,"dry_branch",at)||!consumeOne(chronicle,"field_stone",at)||!consumeOne(chronicle,"plant_fiber",at))throw new IllegalStateException("Material changed."); UUID spear=createCarriedItem(chronicle,"primitive_spear","Primitive spear",at,"CRAFTED"); equip(spear,"HAND_RIGHT","ATTACHED"); return new ItemView(spear,"Primitive spear","primitive_spear",chronicle,null); }
    @Transactional public ItemView craftPrimitiveTool(String itemKey, String displayName, boolean needsBranch, Instant at) { UUID chronicle=activeChronicle(); if(!hasAtLeast(chronicle,"field_stone",1)||!hasAtLeast(chronicle,"plant_fiber",1)||(needsBranch&&!hasAtLeast(chronicle,"dry_branch",1)))throw new IllegalStateException("Insufficient physical material."); if(!consumeOne(chronicle,"field_stone",at)||!consumeOne(chronicle,"plant_fiber",at)||(needsBranch&&!consumeOne(chronicle,"dry_branch",at)))throw new IllegalStateException("Material changed."); UUID tool=createCarriedItem(chronicle,itemKey,displayName,at,"CRAFTED"); equip(tool,"HAND_RIGHT","ATTACHED"); return new ItemView(tool,displayName,itemKey,chronicle,null); }

    /** True if the Chronicle can reach any blade capable of carving wood. */
    @Transactional(readOnly = true)
    public boolean hasCuttingTool(UUID chronicle) { return hasAtLeast(chronicle,"stone_knife",1) || hasAtLeast(chronicle,"stone_hatchet",1); }
    /** Northern-hemisphere season derived from the simulated instant's month, until a dedicated world-clock season exists. */
    private static String seasonOf(Instant at) {
        int month = at.atZone(java.time.ZoneOffset.UTC).getMonthValue();
        return switch (month) {
            case 3, 4, 5 -> "SPRING";
            case 6, 7, 8 -> "SUMMER";
            case 9, 10, 11 -> "AUTUMN";
            default -> "WINTER";
        };
    }

    /** Carve a hearth board and spindle from a dry branch — the reusable friction-fire kit. Needs a blade. */
    @Transactional
    public boolean craftFireKit(Instant at) {
        UUID chronicle=activeChronicle();
        if(!hasCuttingTool(chronicle) || !hasAtLeast(chronicle,"dry_branch",1)) return false;
        if(capacityHeadroomUnits(chronicle,"hearth_board")<=0 || capacityHeadroomUnits(chronicle,"fire_spindle")<=0) return false;
        if(!consumeOne(chronicle,"dry_branch",at)) return false;
        createCarriedItem(chronicle,"hearth_board","Hearth board",at,"CRAFTED");
        createCarriedItem(chronicle,"fire_spindle","Fire spindle",at,"CRAFTED");
        return true;
    }

    /**
     * Make one piece of ignition kit beyond the basic board and spindle (V49).
     * Each of the nine fire-making methods needs its own gear, and a method whose
     * kit cannot be made is a method that does not exist — the flaw the item
     * reachability invariant now guards against.
     *
     * @return [outcome, narration]
     */
    @Transactional
    public String[] craftFireTool(UUID chronicle, String actionText, Instant at) {
        String v = actionText.toLowerCase(java.util.Locale.ROOT);
        record Kit(String key, String name, String needKey, int needQty, boolean blade, String made) { }
        Kit k =
            v.contains("bow")                                   ? new Kit("fire_bow","Fire bow","dry_branch",1,true,"You bend a springy branch and string it with cord until it draws true. The bow will drive a spindle far faster than palms ever could.")
          : v.contains("socket")||v.contains("bearing")||v.contains("handhold") ? new Kit("fire_socket","Bearing block","field_stone",1,true,"You hollow a socket into the stone, smooth enough that the spindle can spin under your whole weight without binding.")
          : v.contains("plough")||v.contains("plow")            ? new Kit("plough_board","Fire plough board","dry_branch",2,true,"You cut a long straight groove down the face of the board — a track for the point to run and pile its own dust ahead of it.")
          : v.contains("saw")                                   ? new Kit("fire_saw_set","Fire saw set","dry_branch",2,true,"You split the wood and notch it, so one piece can be sawn hard across the other.")
          : v.contains("char")                                  ? new Kit("char_tinder","Charred tinder","plant_fiber",2,false,"You char the fiber slowly and smother it before it burns away. What is left will take a spark that raw fiber would shrug off.")
          : v.contains("ember")||v.contains("bundle")           ? new Kit("ember_bundle","Ember bundle","plant_fiber",3,false,"You pack the fiber into a tight bundle with a hollow at its heart — somewhere an ember can travel and stay alive.")
          :                                                       null;
        if (k == null) return new String[]{"FAILED","You turn the wood over in your hands without settling on what to make of it."};
        if (k.blade() && !hasCuttingTool(chronicle))
            return new String[]{"FAILED","The shaping needs a blade, and you have none."};
        if (!hasAtLeast(chronicle, k.needKey(), k.needQty()))
            return new String[]{"FAILED","You have not got the material to hand for that."};
        // An ember bundle is only worth carrying if there is a live fire to take from.
        if ("ember_bundle".equals(k.key())) {
            Boolean live = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fire_state fs JOIN world_object w ON w.id=fs.construction_id JOIN world_object body ON body.current_location_id=w.current_location_id WHERE body.id=? AND fs.active=true)", Boolean.class, chronicle);
            if (!Boolean.TRUE.equals(live)) return new String[]{"FAILED","You build the bundle and hold it ready, but there is no live fire here to take an ember from. It stays cold in your hands."};
        }
        for (int i = 0; i < k.needQty(); i++) consumeOne(chronicle, k.needKey(), at);
        createCarriedItem(chronicle, k.key(), k.name(), at, "CRAFTED");
        return new String[]{"SUCCEEDED", k.made()};
    }

    /**
     * Build a piece of furniture and set it down at the chronicle's location — a
     * world object fixed to a place, not carried. Woodworking needs a blade and
     * dry branches lashed with fiber; a stone shelf is built up from slabs. A
     * container piece (a shelf) gains storage. Returns false on missing material.
     */
    @Transactional
    public boolean craftFurniture(UUID chronicle, UUID location, String itemKey, String displayName, int branches, int slabs, boolean container, int capMass, int capVol, Instant at) {
        if (branches > 0 && (!hasCuttingTool(chronicle) || !hasAtLeast(chronicle,"dry_branch",branches) || !hasAtLeast(chronicle,"plant_fiber",1))) return false;
        if (slabs > 0 && !hasAtLeast(chronicle,"stone_slab",slabs)) return false;
        if (branches == 0 && slabs == 0) return false;
        for (int i=0;i<branches;i++) if(!consumeOne(chronicle,"dry_branch",at)) return false;
        for (int i=0;i<slabs;i++) if(!consumeOne(chronicle,"stone_slab",at)) return false;
        if (branches > 0) consumeOne(chronicle,"plant_fiber",at);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ITEM',?,?)", id, displayName, location);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,?,'SOUND')", id, itemKey);
        if (container) jdbc.update("INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml) VALUES (?,?,?)", id, capMass, capVol);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CRAFTED',jsonb_build_object('recipe',?,'placedAt',?::text))", id, Timestamp.from(at), itemKey, location.toString());
        return true;
    }
    /** Tease a plant-fiber bundle into a fine, dry nest that can catch an ember. */
    @Transactional
    public boolean craftTinder(Instant at) {
        UUID chronicle=activeChronicle();
        if(!hasAtLeast(chronicle,"plant_fiber",1)) return false;
        if(capacityHeadroomUnits(chronicle,"tinder_nest")<=0) return false;
        if(!consumeOne(chronicle,"plant_fiber",at)) return false;
        createCarriedItem(chronicle,"tinder_nest","Tinder nest",at,"CRAFTED");
        return true;
    }

    @Transactional
    public void placeInContainer(UUID item, UUID container) {
        UUID chronicle=activeChronicle(); assertAccessible(item,chronicle); assertAccessible(container,chronicle);
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?",item); jdbc.update("DELETE FROM item_containment WHERE item_id=?",item);
        jdbc.update("INSERT INTO item_containment (item_id,container_id) VALUES (?,?)",item,container);
        jdbc.update("UPDATE world_object SET current_owner_id=?,current_location_id=NULL WHERE id=?",container,item);
        assertCarryCapacity(chronicle);
        jdbc.update("INSERT INTO object_transition (object_id,transition_type,to_container_id,payload) VALUES (?,'PLACED_IN_CONTAINER',?, '{}'::jsonb)",item,container);
    }

    @Transactional
    public void equip(UUID item,String position,String layer) {
        UUID chronicle=activeChronicle(); assertAccessible(item,chronicle);
        Integer compatible=jdbc.queryForObject("SELECT COUNT(*) FROM item_instance i JOIN item_equipment_compatibility c ON c.item_key=i.item_key WHERE i.object_id=? AND c.body_position=? AND c.layer=?",Integer.class,item,position,layer);
        if(compatible==null||compatible==0) throw new IllegalArgumentException("This item cannot be attached at that body position and layer.");
        jdbc.update("DELETE FROM item_containment WHERE item_id=?",item); jdbc.update("INSERT INTO equipment_attachment (item_id,chronicle_id,body_position,layer) VALUES (?,?,?,?)",item,chronicle,position,layer);
        jdbc.update("UPDATE world_object SET current_owner_id=?,current_location_id=NULL WHERE id=?",chronicle,item);
        assertCarryCapacity(chronicle);
        jdbc.update("INSERT INTO object_transition (object_id,transition_type,to_attachment,payload) VALUES (?,'EQUIPPED',?, '{}'::jsonb)",item,position+":"+layer);
    }
    @Transactional
    public boolean unequip(UUID item, Instant occurredAt) {
        UUID chronicle = activeChronicle(); assertAccessible(item, chronicle);
        Integer equipped = jdbc.queryForObject("SELECT COUNT(*) FROM equipment_attachment WHERE item_id=?", Integer.class, item);
        if (equipped == null || equipped == 0) return false;
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", item);
        jdbc.update("UPDATE world_object SET current_owner_id=? WHERE id=?", chronicle, item);
        assertCarryCapacity(chronicle);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'UNEQUIPPED','{}'::jsonb)", item, Timestamp.from(occurredAt));
        return true;
    }
    @Transactional
    public boolean drop(UUID item, UUID location, Instant occurredAt) {
        UUID chronicle = activeChronicle(); assertAccessible(item, chronicle);
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", item);
        jdbc.update("DELETE FROM item_containment WHERE item_id=?", item);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL,current_location_id=? WHERE id=?", location, item);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'DROPPED',jsonb_build_object('locationId',?::text))", item, Timestamp.from(occurredAt), location.toString());
        return true;
    }
    /** Retires a physical item without deleting its identity or immutable transition history. */
    @Transactional
    public void retire(UUID item, Instant occurredAt, String transitionType, String itemKey) {
        jdbc.update("DELETE FROM equipment_attachment WHERE item_id=?", item);
        jdbc.update("DELETE FROM item_containment WHERE item_id=?", item);
        Timestamp occurred = Timestamp.from(occurredAt);
        // Where the object met its end — resolved from its own location, its owner's,
        // or, for a carried item, the living chronicle's. The row is kept; only its
        // status changes and the place/cause of destruction are recorded on it.
        UUID where = jdbc.query("SELECT COALESCE(item.current_location_id,(SELECT o.current_location_id FROM world_object o WHERE o.id=item.current_owner_id),(SELECT w.current_location_id FROM world_object w JOIN chronicle c ON c.id=w.id WHERE c.life_state='LIVING')) FROM world_object item WHERE item.id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, item);
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED',destroyed_at=?,destroyed_location_id=?,destroyed_cause=?,current_owner_id=NULL,current_location_id=NULL WHERE id=?", occurred, where, transitionType, item);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,?,jsonb_build_object('itemKey',?,'destroyedLocationId',?::text))", item, occurred, transitionType, itemKey, where == null ? null : where.toString());
    }
    private UUID activeChronicle(){ UUID id=jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING'",rs->rs.next()?rs.getObject(1,UUID.class):null); if(id==null) throw new IllegalStateException("No living Chronicle exists."); return id; }
    private void assertAccessible(UUID item,UUID chronicle){ Integer present=jdbc.queryForObject("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE') SELECT COUNT(*) FROM reachable WHERE id=?",Integer.class,chronicle,item); if(present==null||present==0) throw new IllegalArgumentException("The item is not physically reachable by the Chronicle."); }
    /**
     * How many additional units of an item the Chronicle can still physically carry,
     * from remaining mass and bulk headroom (0 if a single unit exceeds the lift limit).
     * Lets gathering take only what fits instead of failing the whole attempt.
     */
    private int capacityHeadroomUnits(UUID chronicle, String itemKey) {
        LoadState s = loadState(chronicle);
        int[] dims = jdbc.query("SELECT unit_mass_grams,unit_volume_ml FROM item_definition WHERE item_key=?", rs -> rs.next() ? new int[]{rs.getInt(1), rs.getInt(2)} : new int[]{0, 0}, itemKey);
        int unitMass = dims[0], unitVol = dims[1];
        if (unitMass > 0 && unitMass > s.maximumSingleLiftGrams()) return 0;
        int byMass = unitMass > 0 ? Math.max(0, (s.sustainedMassCapacityGrams() - s.massGrams()) / unitMass) : Integer.MAX_VALUE;
        int byVolume = unitVol > 0 ? Math.max(0, (s.directBulkCapacityMl() - s.bulkMl()) / unitVol) : Integer.MAX_VALUE;
        return Math.max(0, Math.min(byMass, byVolume));
    }
    private void assertCarryCapacity(UUID chronicle) {
        LoadState state=loadState(chronicle); Capacity cap=new Capacity(state.sustainedMassCapacityGrams(),state.directBulkCapacityMl(),state.maximumSingleLiftGrams()); Load load=new Load(state.massGrams(),state.bulkMl(),state.heaviestObjectGrams());
        if(load.mass()>cap.mass()||load.volume()>cap.volume()||load.largest()>cap.singleLift()) throw new IllegalStateException("The Chronicle cannot physically carry that load.");
    }
    private LoadState loadState(UUID chronicle) {
        Capacity cap=jdbc.query("SELECT c.sustained_mass_grams, c.direct_bulk_ml, c.maximum_single_lift_grams, COALESCE(a.load_conditioning,0), COALESCE(a.recovery_readiness,.5) FROM chronicle_carry_capacity c LEFT JOIN chronicle_capability_adaptation a ON a.chronicle_id=c.chronicle_id WHERE c.chronicle_id=?",rs->rs.next()?new Capacity((int)(rs.getInt(1)*(1+rs.getDouble(4)*.12*rs.getDouble(5))),rs.getInt(2),(int)(rs.getInt(3)*(1+rs.getDouble(4)*.08*rs.getDouble(5)))):new Capacity(0,0,0),chronicle);
        Load load=jdbc.query("WITH RECURSIVE carried(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN carried c ON ic.container_id=c.id) SELECT COALESCE(SUM(d.unit_mass_grams),0),COALESCE(SUM(d.unit_volume_ml),0),COALESCE(MAX(d.unit_mass_grams),0) FROM carried JOIN item_instance i ON i.object_id=carried.id JOIN item_definition d ON d.item_key=i.item_key",rs->rs.next()?new Load(rs.getInt(1),rs.getInt(2),rs.getInt(3)):new Load(0,0,0),chronicle);
        return new LoadState(load.mass(),load.volume(),load.largest(),cap.mass(),cap.volume(),cap.singleLift());
    }
    private List<ContainerView> containers(UUID chronicle) {
        return jdbc.query("WITH RECURSIVE reachable(id) AS (SELECT id FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE' UNION ALL SELECT ic.item_id FROM item_containment ic JOIN reachable r ON r.id=ic.container_id JOIN world_object nested ON nested.id=ic.item_id WHERE nested.lifecycle_state='ACTIVE'), contents(container_id,id) AS (SELECT ic.container_id,ic.item_id FROM item_containment ic UNION ALL SELECT c.container_id,ic.item_id FROM contents c JOIN item_containment ic ON ic.container_id=c.id) SELECT w.id,w.display_name,cp.max_mass_grams,cp.max_volume_ml,COALESCE(SUM(d.unit_mass_grams),0),COALESCE(SUM(d.unit_volume_ml),0) FROM reachable r JOIN container_properties cp ON cp.object_id=r.id JOIN world_object w ON w.id=r.id LEFT JOIN contents ct ON ct.container_id=r.id LEFT JOIN item_instance i ON i.object_id=ct.id LEFT JOIN item_definition d ON d.item_key=i.item_key GROUP BY w.id,w.display_name,cp.max_mass_grams,cp.max_volume_ml ORDER BY w.display_name",(rs,row)->new ContainerView(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getInt(6)),chronicle);
    }
    private record Capacity(int mass,int volume,int singleLift){} private record Load(int mass,int volume,int largest){}
    public record ItemView(UUID id,String displayName,String itemKey,UUID ownerId,UUID containerId){}
    public record EquippedView(UUID id,String displayName,String itemKey,String bodyPosition,String layer){}
    public record LoadState(int massGrams,int bulkMl,int heaviestObjectGrams,int sustainedMassCapacityGrams,int directBulkCapacityMl,int maximumSingleLiftGrams){}
    public record ContainerView(UUID id,String displayName,int maxMassGrams,int maxVolumeMl,int usedMassGrams,int usedVolumeMl){}
    public record ItemState(UUID chronicleId,List<ItemView> carried,List<EquippedView> equipped,LoadState load,List<ContainerView> containers){}
}
