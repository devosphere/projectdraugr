package com.devosphere.draugr.chronicle;

import com.devosphere.draugr.item.PhysicalItemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Authoritative physiology runtime. The HUD receives only qualitative states. */
@Service
public class ChroniclePhysiologyService {
    // STARVATION_DEATH_HOURS is gone with it (#960): death follows starvation_severity now, and nothing
    // else asked the clock. A constant no one reads is a claim about the world that the world has stopped
    // making.
    private static final double DEHYDRATION_DEATH_HOURS = 73.0;
    private final JdbcTemplate jdbc;
    /** #953's causal ledger, so the tick can account for what it does to the body and not only leave it. */
    private final com.devosphere.draugr.capability.CausalMetricService metrics;
    public ChroniclePhysiologyService(JdbcTemplate jdbc,
                                     com.devosphere.draugr.capability.CausalMetricService metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public BodyHudSnapshot activeBody() {
        return jdbc.query("SELECT b.condition_summary, p.hours_without_food, p.hours_without_water, p.energy_level, p.core_temperature_c, p.wetness_level, p.bladder_level, p.bowel_level, p.hygiene_level, p.sleep_debt_hours, p.pain_level, p.stress_level, p.injury_severity, p.illness_severity, p.blood_loss_ml, p.stomach_fullness_ml, p.energy_reserves, p.nutrition, p.starvation_severity FROM chronicle c JOIN chronicle_body b ON b.chronicle_id = c.id JOIN chronicle_physiology p ON p.chronicle_id = c.id WHERE c.life_state = 'LIVING'", rs -> { if (!rs.next()) return null; return snapshot(vitalsOf(rs)); });
    }

    @Transactional
    public void advanceTo(Instant now) {
        jdbc.query("SELECT c.id, p.last_metabolic_update, p.hours_without_food, p.hours_without_water, p.energy_level, p.core_temperature_c, p.wetness_level, p.bladder_level, p.bowel_level, p.hygiene_level, b.health, b.condition_summary, p.sleep_debt_hours, p.pain_level, p.stress_level, p.injury_severity, p.illness_severity, p.blood_loss_ml, p.stomach_fullness_ml, p.energy_reserves, p.nutrition, p.starvation_severity, p.stomach_kcal, p.stomach_digest_ml_per_hour FROM chronicle c JOIN chronicle_physiology p ON p.chronicle_id = c.id JOIN chronicle_body b ON b.chronicle_id = c.id WHERE c.life_state = 'LIVING' FOR UPDATE", rs -> {
            if (!rs.next()) return null;
            UUID id = rs.getObject(1, UUID.class);
            Timestamp ts = Timestamp.from(now);
            Instant last = rs.getTimestamp(2).toInstant();
            double hours = Math.max(0, Duration.between(last, now).toSeconds() / 3600.0);
            double food = rs.getBigDecimal(3).doubleValue() + hours;
            double water = rs.getBigDecimal(4).doubleValue() + hours;
            int energy = clamp((int) Math.round(rs.getInt(5) - hours * 0.5));
            // The nutrition four, read here with the rest of the raw state (#959/#960). Thermoregulation
            // below asks what there is to burn, so it needs them as they stand at the START of the tick --
            // the same footing as food, water and energy.
            int fullness   = rs.getInt(19);
            int reserves   = rs.getInt(20);
            int nutrition  = rs.getInt(21);
            int starvation = rs.getInt(22);
            // WHAT THE BULK IS MADE OF (#962). Before this, digestion could only see millilitres, so a
            // stomachful of watercress fed a body as well as a stomachful of pemmican.
            int stomachKcal = rs.getInt(23);
            int digestRate  = Math.max(20, rs.getInt(24));
            // Kept so the tick can account for what it changed, rather than only leaving the result (#960).
            final int reservesAtStart = reserves, starvationAtStart = starvation;

            // Passive baselines only. Actions, food, water, weather, illness, and sanitation
            // will apply additional physiological consequences through the simulation agent.
            int wetness = clamp((int) Math.round(rs.getInt(7) - hours * 4));
            int bladder = clamp((int) Math.round(rs.getInt(8) + hours * 8));
            int bowel = clamp((int) Math.round(rs.getInt(9) + hours * 1.5));
            // A camp latrine and refuse pit (#127/#218) keep filth away from where a Chronicle lives, so
            // grubbiness gathers more slowly — the passive hygiene loss is halved while one stands at the site.
            // This feeds the existing low-hygiene illness pressure below, so a clean camp is a healthier one.
            boolean latrine = Boolean.TRUE.equals(jdbc.queryForObject(
                // Asked of the data (#77, V388), not of the string 'LATRINE' that three readers each held their
                // own copy of — the drift shelters_stock had before V293.
                "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object lt ON lt.id=cp.object_id " +
                "JOIN construction_kind ck ON ck.project_kind=cp.project_kind " +
                "JOIN world_object body ON body.current_location_id=lt.current_location_id " +
                "WHERE body.id=? AND ck.takes_relief AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND lt.lifecycle_state='ACTIVE')",
                Boolean.class, id));
            int hygiene = clamp((int) Math.round(rs.getInt(10) - hours * (latrine ? .125 : .25)));
            double sleepDebt = Math.min(72, rs.getBigDecimal(13).doubleValue() + hours);
            int pain = rs.getInt(14); int stress = rs.getInt(15); int injury = rs.getInt(16); int illness = rs.getInt(17); int bloodLoss = rs.getInt(18);
            Environment environment = jdbc.query("SELECT ww.weather_kind,ww.intensity,ww.ambient_temperature_c,ww.wind_speed_kph,COALESCE((SELECT fs.fuel_minutes FROM construction_project cp JOIN fire_state fs ON fs.construction_id=cp.object_id JOIN world_object pit ON pit.id=cp.object_id JOIN world_object body ON body.current_location_id=pit.current_location_id WHERE body.id=c.id AND fs.active=true ORDER BY fs.fuel_minutes DESC LIMIT 1),0),EXISTS(SELECT 1 FROM construction_project cp JOIN world_object shelter ON shelter.id=cp.object_id JOIN world_object body ON body.current_location_id=shelter.current_location_id WHERE body.id=c.id AND cp.project_kind='LEAN_TO' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND shelter.lifecycle_state='ACTIVE')" +
                ",EXISTS(SELECT 1 FROM construction_project cp JOIN world_object wb ON wb.id=cp.object_id JOIN world_object body ON body.current_location_id=wb.current_location_id WHERE body.id=c.id AND cp.project_kind='WINDBREAK' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND wb.lifecycle_state='ACTIVE')" +
                ",EXISTS(SELECT 1 FROM construction_project cp JOIN world_object rc ON rc.id=cp.object_id JOIN world_object body ON body.current_location_id=rc.current_location_id WHERE body.id=c.id AND cp.project_kind='RAIN_COVER' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND rc.lifecycle_state='ACTIVE')" +
                ",EXISTS(SELECT 1 FROM construction_project cp JOIN world_object ss ON ss.id=cp.object_id JOIN world_object body ON body.current_location_id=ss.current_location_id WHERE body.id=c.id AND cp.project_kind='SUNSHADE' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND ss.lifecycle_state='ACTIVE')" +
                ",(EXISTS(SELECT 1 FROM construction_project cp JOIN world_object en ON en.id=cp.object_id JOIN world_object body ON body.current_location_id=en.current_location_id WHERE body.id=c.id AND " + com.devosphere.draugr.construction.Shelters.ENCLOSING + " AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND en.lifecycle_state='ACTIVE') OR " + com.devosphere.draugr.construction.Shelters.lentByAPeople("c.id") + ")" +
                ",(EXISTS(SELECT 1 FROM item_instance ii JOIN world_object hw ON hw.id=ii.object_id JOIN world_object body ON body.current_location_id=hw.current_location_id WHERE body.id=c.id AND ii.item_key='smoke_hood' AND hw.lifecycle_state='ACTIVE')" +
                " OR EXISTS(SELECT 1 FROM construction_project cp JOIN world_object sv ON sv.id=cp.object_id JOIN world_object body ON body.current_location_id=sv.current_location_id WHERE body.id=c.id AND cp.project_kind='SMOKE_VENT' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND sv.lifecycle_state='ACTIVE'))" +
                ",EXISTS(SELECT 1 FROM equipment_attachment e JOIN item_instance ii ON ii.object_id=e.item_id WHERE e.chronicle_id=c.id AND ii.item_key='smoke_face_wrap')" +
                // 13: the ground the body is actually standing on, so the weather it endures can be the
                // weather that falls THERE rather than the world's single sky. See the note on the extractor.
                ",wc.id" +
                " FROM chronicle c JOIN world_object standing ON standing.id=c.id" +
                " LEFT JOIN world_chunk wc ON wc.id=standing.current_location_id" +
                // THE SKY WHERE THE BODY STANDS, not the world's only front (#912). A body three chunks
                // outside the storm is not being rained on, and this is the query that decides whether it is.
                " CROSS JOIN LATERAL local_sky(wc.id) ww WHERE c.id=?", result -> {
                    if (!result.next()) return new Environment("CLEAR",0,18,0,0,false,false,false,false,false,false,false);
                    // The body endures the weather where it STANDS (#37). Everything else in the simulation had
                    // already learned this: the Body HUD, the prose, the backdrops and the senses all run the
                    // world's single sky through BiomeClimate first, so a peak is colder and windier than the
                    // valley under one front. The physiology read went straight to world_weather and never
                    // touched world_chunk — so a Chronicle on a mountain was chilled as though they were in the
                    // lowlands, and a Chronicle sheltering inside a cave took the open mountain's wind.
                    //
                    // The gap is not small. Across this world the lapse-plus-latitude term alone runs to -15.6C
                    // on mountain ground and averages -9.9C there; CAVE_INTERIOR carries a wind offset of -200,
                    // which is the entire reason anything dens in one. Altitude was decoration: climbing cost
                    // the body nothing, and a cave sheltered it from nothing.
                    //
                    // The reading is LocalWeatherService's, the one the HUD, the prose and the build gates use, so
                    // the body cannot be chilled by a different sky from the one it is told about. Only a body
                    // standing nowhere falls back to the front unmodulated.
                    UUID ground = result.getObject(13, UUID.class);
                    com.devosphere.draugr.simulation.BiomeClimate.Local here = ground != null
                        ? com.devosphere.draugr.simulation.LocalWeatherService.at(jdbc, ground)
                        : com.devosphere.draugr.simulation.BiomeClimate.at(null, 0, 500, 0, 1,
                            result.getString(1), result.getBigDecimal(3).doubleValue(), result.getInt(4));
                    // Phase comes from the local reading too: snow does not soak a body the way rain does, and a
                    // cold storm is a blizzard. Intensity stays global — that is the front's own strength.
                    return new Environment(here.kind(), result.getInt(2), here.temperatureC(), here.windKph(),
                        result.getInt(5), result.getBoolean(6), result.getBoolean(7), result.getBoolean(8),
                        result.getBoolean(9), result.getBoolean(10), result.getBoolean(11), result.getBoolean(12));
                }, id);
            double core = rs.getBigDecimal(6).doubleValue();
            // Full shelter cuts the wind most; a bare-hand windbreak (#195) cuts it partly, but it is not shelter.
            double effectiveWind = environment.shelter() ? environment.wind() * .25 : (environment.windbreak() ? environment.wind() * .5 : environment.wind());
            // What the chronicle is wearing finally counts. Summed insulation across worn
            // garments is capped at 80%, so no stack of rags makes a body invulnerable —
            // clothing buys time against the cold, it does not repeal it.
            Integer worn = jdbc.queryForObject(
                // Plus whatever THIS garment has been stuffed or lined with (#37, V391). A property of the
                // object, not of its kind: these boots are packed with dry grass and those ones are not.
                "SELECT COALESCE(SUM(d.insulation_value + COALESCE(i.lining_bonus,0)),0) FROM equipment_attachment e " +
                "JOIN item_instance i ON i.object_id=e.item_id " +
                "JOIN item_definition d ON d.item_key=i.item_key " +
                "JOIN world_object w ON w.id=e.item_id AND w.lifecycle_state='ACTIVE' " +
                "WHERE e.chronicle_id=?", Integer.class, id);
            double retained = 1.0 - Math.min(0.80, (worn == null ? 0 : worn) / 100.0);
            // Worn garments that SHED rain — hide, fur, leather, bark, a reed hat — keep the body far drier in the
            // wet than woven cloth, which soaks (#177 waterproofing). Summed water resistance across what is worn,
            // capped, so no outfit stays bone-dry in a storm — it slows the soaking, it does not repeal the rain.
            // Closes the water_resistance dead-read: garments could be waterproofed/oiled/hardened, but nothing read
            // it, so a waxed hide cloak soaked as fast as bare skin.
            Integer shed = jdbc.queryForObject(
                "SELECT COALESCE(SUM(d.water_resistance),0) FROM equipment_attachment e " +
                "JOIN item_instance i ON i.object_id=e.item_id " +
                "JOIN item_definition d ON d.item_key=i.item_key " +
                "JOIN world_object w ON w.id=e.item_id AND w.lifecycle_state='ACTIVE' " +
                "WHERE e.chronicle_id=?", Integer.class, id);
            double rainShed = 1.0 - Math.min(0.60, (shed == null ? 0 : shed) / 40.0);
            // Wet clothing insulates far worse than dry — the classic way people die of
            // cold in weather that is not, in itself, lethal.
            if (wetness >= 60) retained = Math.min(1.0, retained * 1.6);
            double envExposure = ((environment.ambient() - core) * Math.min(.22, hours * .04) - effectiveWind * hours * .004) * retained;
            // A leaf sunshade (#195) cuts the radiant heat load when the air is hotter than the body — partial
            // relief from a hot day, not a cool room. It does nothing in the cold (envExposure is then negative).
            if (environment.sunShade() && environment.ambient() > core) envExposure *= 0.6;
            double exposure = envExposure;
            // A living body makes its own heat, and that is what actually holds core
            // temperature at 37C in air far colder than 37C. Without this term the model
            // had nothing opposing environmental drain, so core decayed toward ambient
            // and any ambient below 28C was eventually fatal regardless of preparation.
            // Fuel matters: a starving or exhausted body thermoregulates poorly, which
            // is why cold takes the hungry first — and why food, fire, and clothing all
            // have to fail together before a chronicle freezes.
            // WHAT THERE IS TO BURN, not how long since a meal (#960). This asked the clock -- a Chronicle
            // who had eaten an hour ago had full metabolic vigour however empty their reserves were, and one
            // who had eaten nothing for three weeks but had just had a berry was treated the same. It is the
            // store and the damage that decide whether a body can keep itself warm.
            double metabolicVigour = (reserves <= 2 || starvation >= 55 || energy < 15) ? .35 : 1.0;
            exposure += (37.0 - core) * Math.min(.40, hours * .07) * metabolicVigour;
            // Fire warming scales with fuel level: dying embers (1-30 min) = low, steady (31-90) = moderate, well-fed (91+) = good.
            // A primitive open fire pit is less effective than an enclosed hearth — capped accordingly.
            if (environment.fuelMinutes() > 0) {
                double fireRate = environment.fuelMinutes() <= 30 ? 0.6 : environment.fuelMinutes() <= 90 ? 1.4 : 2.0;
                exposure += (37.0 - core) * Math.min(0.5, hours * fireRate);
            }
            core = core + exposure;
            // A body cannot be chilled below the air around it. Wind and wet carry heat
            // away faster, but they cannot take a body past equilibrium with its
            // surroundings — without this floor the uncapped wind term drove core
            // temperature below ambient without limit, and every chronicle froze to
            // death on a mild day. Cold air still kills; a 20C day no longer does.
            if (environment.fuelMinutes() == 0 && environment.ambient() < 37.0)
                core = Math.max(Math.min(environment.ambient(), 36.6), core);
            core = Math.max(20, Math.min(45, core));
            // Rain wets the body less under a roof; a leant rain cover (#195) sheds some of it, but far less than
            // true shelter — it has no walls and turns only the worst of a shower.
            if ("RAIN".equals(environment.kind()) || "STORM".equals(environment.kind())) wetness = clamp((int)Math.round(wetness + hours * (environment.intensity() / (environment.shelter() ? 28.0 : (environment.rainCover() ? 15.0 : 7.0))) * rainShed));
            if (environment.fuelMinutes() > 0) wetness = clamp((int)Math.round(wetness - hours * 14));
            // Woodsmoke in an enclosed shelter with an unvented fire fouls the air (#198): a small, non-lethal
            // pressure toward illness. A smoke hood at the hearth vents it away entirely; a worn smoke face wrap
            // only cuts what is breathed, so it eases but does not end the exposure — the real fix is the vent.
            boolean smoke = environment.enclosed() && environment.fuelMinutes() > 0 && !environment.vented();
            int smokePressure = smoke ? (environment.smokeWrap() ? (int) Math.ceil(hours * .15) : (int) Math.ceil(hours * .4)) : 0;
            // A camp choked with refuse (#218) breeds disease: filth left to pile up where a Chronicle lives is a
            // non-lethal pressure toward illness, like a foul body or a smoky shelter. A built latrine/refuse pit
            // keeps the ground clean (it disposes of refuse in the world tick), so this only bites a neglected camp.
            // Zero when the ground is clean or has no refuse row at all.
            int refuse = jdbc.queryForObject(
                "SELECT COALESCE((SELECT cr.refuse_level FROM chunk_refuse cr JOIN world_object body ON body.current_location_id=cr.chunk_id WHERE body.id=?),0)",
                Integer.class, id);
            int refusePressure = refuse >= 60 ? (int) Math.ceil(hours * .5) : 0;
            int illnessPressure = (hygiene <= 10 ? (int) Math.ceil(hours * .4) : 0) + (wetness >= 70 && core < 36.0 ? (int) Math.ceil(hours * .6) : 0) + (injury >= 60 ? (int) Math.ceil(hours * .15) : 0) + smokePressure + refusePressure;
            // AND THE BODY MENDS (#909). Illness only ever rose. Every source of it above is answerable —
            // wash, get dry, get warm, vent the hearth, dig a latrine, dress the wound that is feeding it —
            // and answering every one of them left the Chronicle exactly as sick as before, with illness
            // above 80 meaning Critical and 100 meaning death. A Chronicle who had put right everything the
            // simulation held against them still had nowhere to go but down. That is not a survival game;
            // it is a countdown with chores.
            //
            // Mending only when NOTHING is still driving it — a body does not fight off a sickness while
            // sitting soaked in a smoke-filled hut — and then at a rate set by how well it is being kept.
            // Warm, dry, clean, fed and watered, rested: three of those to mend at all, and all five to mend
            // at the speed the worst single pressure above would have raised it. Deliberately slow. Getting
            // out of this is days of looking after yourself, which is the point.
            int mending = illnessPressure > 0 || illness <= 0 ? 0
                        : illnessMended(hours, howWellKept(core, wetness, hygiene, reserves, nutrition, starvation, water, sleepDebt));
            // WHICH WAY IT IS GOING, written down by the one piece of code that knows (#909). The reading
            // repeats this rather than working it out a second time against a partial view of the world.
            int illnessTrend = illnessPressure - mending;
            // A tick in which no time passed measured nothing, so it must not overwrite what the last real one
            // measured. Every resolved action ticks the body to the world clock first — including `check
            // myself`, the act that READS this — and a zero-hour tick computes zero pressure and zero mending,
            // so it reported a sickness being fed by filth as "holding where it is" (CI, #909). The update
            // below keeps the stored trend unless hours > 0.
            illness = clamp(illness + illnessPressure - mending);
            // ============ WHAT A BODY ACTUALLY DOES WITH FOOD (#959) ============
            // Four things, because there are four things, and one counter was doing all of them. A stomach
            // empties into a store; the store is spent by living; when the store is gone the body begins
            // taking itself apart, and that damage is not undone by the next mouthful.
            // DIGESTION: the stomach empties at a steady rate, and what leaves it becomes reserve. A full
            // stomach (1200 ml) takes about thirteen hours to clear, which is why a meal carries you
            // through a night and not through a week.
            // AT THE RATE OF WHAT IS IN THERE (#962). The flat 90 ml/h treated a skin of water and a
            // slab of pemmican as the same job of work. digest_ml_per_hour is the bulk-weighted mean of
            // the foods actually swallowed, so water is gone in minutes and fat sits for half a day.
            int digested = (int) Math.min(fullness, Math.round(hours * digestRate));
            // AND THE NOURISHMENT LEAVES IN STEP WITH THE BULK IT CAME IN WITH. Pro rata rather than
            // per-item, because a stomach is a mixture and tracking every mouthful in it would be a
            // second account of the same thing -- the exact mistake #959 took apart.
            int kcalReleased = fullness > 0 ? (int) Math.round(stomachKcal * (digested / (double) fullness)) : 0;
            fullness -= digested;
            stomachKcal = fullness == 0 ? 0 : Math.max(0, stomachKcal - kcalReleased);
            // Nutrition decides how much good the food does: a poorly nourished gut takes up less of what
            // passes through it. Bounded well away from zero so a poorly fed body still gets something
            // from what it eats -- it has to, or it could never climb out.
            double worth = 0.55 + 0.45 * (nutrition / 100.0);
            // KCAL_PER_RESERVE_POINT is the whole bridge between the catalogue's real numbers and the
            // body's store. See its javadoc for why 110 leaves the staple diet worth what it was.
            double gained = kcalReleased / (double) KCAL_PER_RESERVE_POINT * worth;

            // EXPENDITURE: basal cost, plus the cold, plus being ill. Work is charged separately by the act
            // that did it, as it always has been.
            double basal = hours * 0.85;
            double chill = core < 36.5 ? hours * Math.min(1.6, (36.5 - core) * 0.35) : 0;
            double fever = illness > 35 ? hours * 0.45 : 0;
            double spent = basal + chill + fever;

            double store = reserves + gained - spent;
            // STARVATION: only once the store is gone. A body with reserves is hungry; a body without them
            // is starving, and that is a different thing -- which is the distinction #959 asks for and the
            // old single counter could not make.
            if (store < 0) {
                starvation = clamp((int) Math.round(starvation + Math.min(-store, hours * 0.9) * 0.5));
                store = 0;
            } else if (store > 0 && digested > 0 && starvation > 0) {
                // AND IT MENDS, slowly, only while genuinely fed, and SLOWER THE DEEPER IT IS (#960: "Severe
                // deprivation should take longer to recover from than a short deficit"). A body that has
                // spent weeks taking itself apart has to put itself back together, and the putting back is
                // not the same work as the taking apart.
                //
                // Hydration and rest have to be there too, which is #960's "consistent nutrition, hydration,
                // rest and reduced exertion gradually reverse starvation damage" -- a body mending on no
                // water and no sleep is not mending.
                double depth = 1.0 - Math.min(0.75, starvation / 140.0);
                double kept = (water < 18 ? 1.0 : 0.45) * (sleepDebt < 20 ? 1.0 : 0.6);
                double mend = hours * 0.11 * depth * kept;
                starvation = clamp((int) Math.round(starvation - Math.min(mend, 4)));
            }
            reserves = clamp((int) Math.round(Math.min(100, store)));

            // NUTRITION follows what has actually been going in, over weeks rather than hours. Fed, it
            // climbs; unfed, it slips; and it moves slowly enough that a single good meal does not make a
            // well-nourished body of a starved one.
            // AND IT FOLLOWS WHAT THE FOOD ACTUALLY WAS (#962). `digested > 0` was true of a stomach
            // full of leaves, so a body could live on watercress and grow better nourished every day.
            // Energy density is the whole difference: about 1.4 kcal/ml for a mixed diet, 0.1 for greens.
            double density = digested > 0 ? kcalReleased / (double) digested : 0;
            double dietDrift = digested == 0 ? -hours * 0.055
                             : density >= 0.80 ? hours * 0.09
                             : density >= 0.35 ? hours * 0.02
                             :                  -hours * 0.03;
            if (reserves == 0) dietDrift -= hours * 0.05;
            nutrition = clamp((int) Math.round(nutrition + dietDrift));

            // AND ENERGY CANNOT EXCEED WHAT THERE IS TO SPEND (#960: starvation "must influence Health,
            // Energy, Condition..."). Energy is what a body can do right now and reserves are what it has to
            // do it with, so a Chronicle with an empty store cannot be Rested however long they have lain
            // down -- they can be less exhausted, and that is all. The floor of 20 is deliberate: a starving
            // body can still get up, which is why starvation takes weeks to kill rather than hours.
            // AND THE ACCOUNT OF IT (#960: "Record why starvation worsens or improves"). The cause is named
            // from what actually drove the deficit, which the tick is the only thing that knows: a fever
            // burning the store, the cold burning it, or simply nothing going in. It goes to #953's ledger,
            // so a Chronicle's starvation can be reconstructed afterwards rather than merely observed.
            if (starvation != starvationAtStart) {
                String why = fever > chill && fever > 0 ? "ILLNESS"
                           : chill > 0 ? "EXPOSURE"
                           : "NOURISHMENT";
                metrics.recordApplied(id, "STARVATION_SEVERITY", starvationAtStart, starvation,
                    starvation > starvationAtStart ? why : "NOURISHMENT", null,
                    starvation > starvationAtStart
                        ? (fever > chill && fever > 0 ? "a sickness burning through the store"
                           : chill > 0 ? "the cold burning through the store" : "nothing going in")
                        : "being fed, watered and rested",
                    (int) Math.max(1, Math.round(hours * 60)), now);
            }
            if (reserves != reservesAtStart)
                metrics.recordApplied(id, "ENERGY_RESERVES", reservesAtStart, reserves,
                    reserves > reservesAtStart ? "NOURISHMENT" : "EXPOSURE", null,
                    reserves > reservesAtStart ? "food digesting" : "the cost of being alive",
                    (int) Math.max(1, Math.round(hours * 60)), now);

            int energyCeiling = 20 + (int) Math.round(reserves * 0.8);
            if (energy > energyCeiling) energy = energyCeiling;
            // Deep starvation takes the ceiling down further: a body this far gone has nothing to give.
            if (starvation >= 60) energy = Math.min(energy, 35);
            // Every physiological death vector terminates here. Trauma (injury_severity
            // at its ceiling) is the path a grievous mauling or an accumulation of
            // untreated wounds takes: without it, a chronicle savaged to the limit
            // would live on indefinitely, since a single wound rarely bleeds past the
            // exsanguination threshold on its own.
            // STARVATION KILLS, NOT THE CLOCK (#960). The old rule was "721 hours since eating", which meant
            // a Chronicle who had eaten well every other day for a month died on the same schedule as one who
            // had eaten nothing, because the counter was reset by a mouthful and the threshold never asked
            // what the mouthful had done. starvation_severity is the damage itself, so death now follows the
            // damage. The elapsed-hours clause is GONE from this condition, which is what #960 asks for:
            // "Replace the fixed elapsed-food death rule with the implemented starvation model."
            if (starvation >= 100 || water > DEHYDRATION_DEATH_HOURS || bloodLoss > 3500 || injury >= 100 || illness >= 100 || core < 28.0 || core > 42.0) {
                String cause = water > DEHYDRATION_DEATH_HOURS ? "Critical Dehydration" : starvation >= 100 ? "Critical Starvation" : bloodLoss > 3500 ? "Critical Blood Loss" : injury >= 100 ? "Fatal Trauma" : core < 28.0 ? "Severe Hypothermia" : core > 42.0 ? "Severe Hyperthermia" : "Systemic Illness";
                // The body as it was at the moment it stopped, recorded whole (#957): the snapshot used to
                // label a Chronicle who died of cold with a health read from injury and illness alone.
                Vitals dying = new Vitals(injury, illness, bloodLoss, core, food, water, energy, sleepDebt, wetness, hygiene, bladder, bowel, pain, stress, fullness, reserves, nutrition, starvation);
                UUID deathLocation = jdbc.query("SELECT current_location_id FROM world_object WHERE id=?", result -> result.next() ? result.getObject(1, UUID.class) : null, id);
                relocatePossessions(id, deathLocation, now);
                releaseTheirStock(id, ts);
                jdbc.update("INSERT INTO chronicle_death_snapshot (chronicle_id,died_at,death_location_id,cause,body_snapshot) VALUES (?,?,?,?,jsonb_build_object('health',?,'condition',?,'hunger',?,'thirst',?,'energy',?,'temperature',?,'wetness',?,'bladder',?,'bowel',?,'hygiene',?))",id,ts,deathLocation,cause,health(dying),conditionSummary(dying),hungerFromHours(food),thirst(water),energy(energy),temperature(core),wetness(wetness),bladder(bladder),bowel(bowel),hygiene(hygiene));
                jdbc.update("UPDATE chronicle SET life_state = 'DEAD', died_at = ?, death_cause = ? WHERE id = ?", ts, cause, id);
                jdbc.update("UPDATE chronicle_body SET health = 'Dying', condition_summary = ? WHERE chronicle_id = ?", cause, id);
                jdbc.update("INSERT INTO chronicle_event (chronicle_id, occurred_at, event_type, payload) VALUES (?, ?, 'CHRONICLE_DIED', jsonb_build_object('cause', ?))", id, ts, cause);
                jdbc.update("INSERT INTO world_event (occurred_at, event_type, aggregate_id, payload) VALUES (?, 'CHRONICLE_DIED', ?, jsonb_build_object('cause', ?))", ts, id, cause);
            } else {
                jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = ?, hours_without_food = ?, hours_without_water = ?, energy_level = ?, core_temperature_c=?, wetness_level = ?, bladder_level = ?, bowel_level = ?, hygiene_level = ?, sleep_debt_hours=?, pain_level=?, stress_level=?, injury_severity=?, illness_severity=?, blood_loss_ml=?, illness_trend=CASE WHEN ? THEN ? ELSE illness_trend END, stomach_fullness_ml=?, stomach_kcal=?, energy_reserves=?, nutrition=?, starvation_severity=? WHERE chronicle_id = ?", ts, BigDecimal.valueOf(food), BigDecimal.valueOf(water), energy, BigDecimal.valueOf(core), wetness, bladder, bowel, hygiene, BigDecimal.valueOf(sleepDebt), pain, stress, injury, illness, bloodLoss, hours > 0, illnessTrend, fullness, stomachKcal, reserves, nutrition, starvation, id);
                // ONE BODY, ONE READING (#957). These locals are the body as this tick leaves it, so the label and the
                // death check above are now looking at the same fourteen numbers rather than three and seven.
                Vitals ticked = new Vitals(injury, illness, bloodLoss, core, food, water, energy, sleepDebt, wetness, hygiene, bladder, bowel, pain, stress, fullness, reserves, nutrition, starvation);
                BodyHudSnapshot body = snapshot(ticked);
                jdbc.update("UPDATE chronicle_body SET hunger = ?, thirst = ?, energy = ?, temperature = ?, wetness = ?, bladder = ?, bowel = ?, hygiene = ? WHERE chronicle_id = ?", body.hunger(), body.thirst(), body.energy(), body.temperature(), body.wetness(), body.bladder(), body.bowel(), body.hygiene(), id);
            }
            return null;
        });
    }

    @Transactional
    public void applyRelief(UUID chronicleId, boolean bowel) {
        String column = bowel ? "bowel_level" : "bladder_level";
        jdbc.update("UPDATE chronicle_physiology SET " + column + " = 0 WHERE chronicle_id = ?", chronicleId);
        jdbc.update("UPDATE chronicle_body SET " + (bowel ? "bowel" : "bladder") + " = 'Empty' WHERE chronicle_id = ?", chronicleId);
    }

    /**
     * The physiological cost of a personal physical act — moderate exertion with
     * no nourishment. Energy falls, the body burns through food and water faster,
     * hygiene degrades, the bladder fills. The tick makes no exception for why the
     * body is spent: repeated without eating, drinking, or resting, this depletes
     * toward exhaustion and dehydration like any other drain. The world applies
     * physics, not judgment. See DR-0019.
     */
    @Transactional
    public void applyPersonalActExertion(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET energy_level=GREATEST(0,energy_level-15), hours_without_food=hours_without_food+2, hours_without_water=hours_without_water+2, hygiene_level=GREATEST(0,hygiene_level-12), bladder_level=LEAST(100,bladder_level+5) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** A trivial exertion — venting frustration at scenery costs a little energy and nothing else. */
    @Transactional
    public void applyMinorExertion(UUID chronicleId, int energyCost) {
        jdbc.update("UPDATE chronicle_physiology SET energy_level=GREATEST(0,energy_level-?) WHERE chronicle_id=?", Math.max(0, energyCost), chronicleId);
        refreshBody(chronicleId);
    }

    /**
     * The physical cost of doing work, on top of the passive metabolic tick — hard labour tires and
     * dirties the body, so felling a tree or hauling stone is not free the way standing still is
     * (GitHub #27). Energy and hygiene fall by the intensity of the act; the survival loop then has to
     * answer for it with food, rest, and washing. No effect when both costs are zero.
     */
    @Transactional
    public void applyLabor(UUID chronicleId, PhysicalItemService items, int energyCost, int hygieneCost) {
        if (energyCost <= 0 && hygieneCost <= 0) return;
        // #77 — the load on your back. Until now the pack was free: a Chronicle hauled ninety kilos of stone across
        // a mountainside and tired exactly as much as one walking empty-handed, because nothing between the carry
        // limit and the body ever asked what was being carried. The limit refused an impossible load and permitted
        // everything under it at no cost, which is the one thing carrying is not.
        //
        // Work under a load costs up to twice the energy, in proportion to how near the body is to what it can
        // shoulder. Measured against the capacity the world actually grants — so conditioning, a carry pole, a yoke
        // or a pack frame all lighten the work they were made to lighten, and a TAMED beast dragging a travois
        // lightens it most of all, because the capacity it adds is capacity the body is not bearing. The beast
        // carries the load, so the body does not pay for it: that falls out of this rather than being written.
        double laden = loadFraction(items, chronicleId);
        energyCost = (int) Math.round(energyCost * (1 + laden));
        // #217 — heavy exertion taxes the body beyond the energy it spends. Hard work (the Labor(12,·) heavy tier;
        // a failed half-effort passes a smaller cost and does neither) sweats the body — it leaves it damp, and that
        // damp then chills through the SAME wetness→cold→illness path as rain does unless dried at a fire or shelter
        // — and it dries the body from within, so a labouring Chronicle thirsts sooner than a resting one and must
        // drink more to keep up. New exposure/hydration vectors: the passive metabolism only ever dampened the body
        // from rain and only ever thirsted it with the passage of time, never from the labour itself.
        int sweat = energyCost >= 12 ? 3 : 0;
        double exertionThirst = energyCost >= 12 ? 0.5 : 0.0;
        jdbc.update("UPDATE chronicle_physiology SET energy_level=GREATEST(0,energy_level-?), hygiene_level=GREATEST(0,hygiene_level-?), wetness_level=LEAST(100,wetness_level+?), hours_without_water=hours_without_water+? WHERE chronicle_id=?",
            Math.max(0, energyCost), Math.max(0, hygieneCost), sweat, exertionThirst, chronicleId);
        refreshBody(chronicleId);
    }
    /**
     * How near this body is to what it can shoulder, 0 to 1 (#77).
     *
     * <p>Read from the same {@code currentLoad} the carry limit itself is judged by, rather than a second sum of
     * masses here — one definition of "laden", so the number that refuses a load and the number that costs a body
     * to carry one can never disagree. A body with no capacity recorded is treated as unladen rather than as
     * infinitely burdened, because a missing row must not silently double every cost in the game.
     */
    private double loadFraction(PhysicalItemService items, UUID chronicleId) {
        if (items == null) return 0;
        var load = items.currentLoad(chronicleId);
        int capacity = load.sustainedMassCapacityGrams();
        if (capacity <= 0) return 0;
        return Math.max(0, Math.min(1.0, load.massGrams() / (double) capacity));
    }
    @Transactional
    public void eat(UUID chronicleId) { eat(chronicleId, com.devosphere.draugr.quality.QualityGrade.SOUND); }
    /** A food's workmanship grade scales how much it nourishes — bounded, so even a poor meal still staves off
     *  hunger and a fine one is only modestly better (#271); grade is a benefit, never a gate. */
    @Transactional
    public void eat(UUID chronicleId, com.devosphere.draugr.quality.QualityGrade grade) {
        eatPortion(chronicleId, ORDINARY_PORTION_ML, grade, 0);
    }

    /** An unremarkable helping: what "eat" means when nothing says how much. #962 gives foods their own. */
    static final int ORDINARY_PORTION_ML = 320;
    /** A prepared meal is a bigger thing than a handful of something. */
    static final int COOKED_MEAL_ML = 700;

    /**
     * EATING FILLS A STOMACH (#959).
     *
     * <p>It used to subtract eight from the hours-since-eating counter, which is why clearing "Starving"
     * took forty-two helpings and why a blackberry relieved exactly as much as a roast fish: the counter
     * knew nothing about portions and the Hunger label was read from the same counter a meal reduced.
     *
     * <p>A portion goes in, bounded by {@link #STOMACH_CAPACITY_ML}. What will not fit does not go in, so
     * eating again immediately achieves nothing -- which is #960's "prevent instant recovery through
     * repeated eating commands", enforced by the stomach rather than by a rule about commands. Digestion
     * turns what is in there into reserves over hours.
     *
     * <p>The hours counter is still written, because it is still true that they have just eaten. It is
     * history now rather than a debt, which is exactly what #959 asks for.
     *
     * <p>AND A MEAL DOES NOT TOUCH {@code starvation_severity}. That is the damage going without has done,
     * and it mends through sustained feeding (#960), not through a mouthful. A Chronicle can finish a large
     * meal, read Hunger "Full", and still be Critical with starvation -- which is #956's own example of the
     * thing the old model could not express.
     */
    @Transactional
    public void eatPortion(UUID chronicleId, int portionMl, com.devosphere.draugr.quality.QualityGrade grade, int stressRelief) {
        // A PORTION WITH NOTHING SAID ABOUT WHAT IT WAS is an ordinary one, at the density the flat model
        // implied (#962): 1.58 kcal/ml is what made a 1200 ml stomachful worth 15.6 reserve points. So every
        // caller that has not yet named its food behaves exactly as it did before.
        swallow(chronicleId, portionMl, (int) Math.round(Math.max(0, portionMl) * 1.58), 0, 90, grade, stressRelief);
    }

    /**
     * EAT ONE UNIT OF A NAMED FOOD (#962).
     *
     * <p>This is the method the game should be calling, and the reason the ticket exists. Everything before
     * it put a flat 320 ml of nothing-in-particular into the stomach, so one bilberry (3 g) filled a quarter
     * of a body and fed it as well as a roast haunch, and a skin of boiled water -- which is category FOOD,
     * and which a bare {@code eat} would happily take -- nourished as much as the haunch.
     *
     * <p>THREE THINGS COME OUT OF THE CATALOGUE, and none of them is invented here:
     * <ul>
     *   <li>bulk: {@code item_definition.unit_volume_ml}, the same number the carry weight reads;</li>
     *   <li>nourishment: {@code kcal_per_100g} over the unit's own mass;</li>
     *   <li>water, emptying rate and comfort: the food's own, from {@code food_nourishment} (V482).</li>
     * </ul>
     *
     * <p>A food with no row is treated as a middling one rather than as poison or as a feast. The migration
     * refuses to apply if a catalogued food lacks a row, and a test holds the same line, so the fallback is
     * for an item invented at runtime -- not a quiet guess standing in for data nobody wrote.
     */
    @Transactional
    public void eatOneOf(UUID chronicleId, String itemKey, com.devosphere.draugr.quality.QualityGrade grade) {
        var worth = jdbc.query(
            "SELECT d.unit_volume_ml, d.unit_mass_grams, f.kcal_per_100g, f.water_percent, " +
            "       f.digestion_ml_per_hour, f.settles_the_nerves " +
            "  FROM item_definition d LEFT JOIN food_nourishment f ON f.item_key = d.item_key " +
            " WHERE d.item_key = ?", rs -> {
                if (!rs.next()) return null;
                int bulk = rs.getInt(1), mass = rs.getInt(2);
                int kcalPer100 = rs.getInt(3);
                if (rs.getObject(3) == null) return new int[]{ bulk > 0 ? bulk : ORDINARY_PORTION_ML,
                    (int) Math.round((bulk > 0 ? bulk : ORDINARY_PORTION_ML) * 1.58), 0, 90, 0 };
                return new int[]{ Math.max(1, bulk), (int) Math.round(mass * kcalPer100 / 100.0),
                    (int) Math.round(mass * rs.getInt(4) / 100.0), Math.max(20, rs.getInt(5)), rs.getInt(6) };
            }, itemKey);
        if (worth == null) { eatPortion(chronicleId, ORDINARY_PORTION_ML, grade, 0); return; }
        swallow(chronicleId, worth[0], worth[1], worth[2], worth[3], grade, worth[4]);
    }

    /** How many real kilocalories one point of {@code energy_reserves} stands for. See V482's header: a
     *  point costs 0.85/hour to live, so a day is 20.4 points, and at 110 kcal to the point a stomachful of
     *  cooked game meat is worth 16.0 points where the old flat model gave a stomachful 15.6. The staple diet
     *  is worth what it was worth; it is the berry, the leaf and the water that change. */
    static final int KCAL_PER_RESERVE_POINT = 110;

    /**
     * THE ONE PLACE FOOD GOES IN.
     *
     * <p>Bulk is capped by the stomach, and {@code kcal} is admitted in the SAME PROPORTION as the bulk that
     * fitted -- so half a meal is half the nourishment, and a body that forces down a second helping on a
     * full stomach gains nothing from it. Getting this wrong in either direction is #962's "no nourishment
     * duplication", and doing it in one place is why it can only be got wrong once.
     *
     * <p>The emptying rate becomes the bulk-weighted mean of what the stomach now holds, which is the only
     * honest answer for a mixture and needs no record of the individual mouthfuls.
     *
     * <p>Thirst goes at 50 ml to the hour -- the rate {@code drink()}'s long-standing ten hours for a 500 ml
     * skin already implied, read out of it rather than chosen again here.
     */
    private void swallow(UUID chronicleId, int bulkMl, int kcal, int waterMl, int rate,
                         com.devosphere.draugr.quality.QualityGrade grade, int stressRelief) {
        double m = nourishmentFactor(grade);
        int bulk = Math.max(0, bulkMl);
        int offeredKcal = (int) Math.round(Math.max(0, kcal) * m);
        jdbc.update(
            // `got` is how much of the portion the stomach had room for. Everything else is in step with it.
            "WITH room AS (SELECT LEAST(?, GREATEST(0, ? - stomach_fullness_ml)) AS got, " +
            "                     stomach_fullness_ml AS was, stomach_digest_ml_per_hour AS at_rate " +
            "                FROM chronicle_physiology WHERE chronicle_id = ?) " +
            "UPDATE chronicle_physiology SET " +
            // Nourishment in the SAME PROPORTION as the bulk that fitted: half a meal is half the good.
            "  stomach_kcal = stomach_kcal + CASE WHEN ? > 0 THEN ROUND(? * room.got / ?::numeric) ELSE 0 END, " +
            // The stomach's emptying rate is the bulk-weighted mean of what is now in it.
            "  stomach_digest_ml_per_hour = CASE WHEN room.was + room.got > 0 " +
            "      THEN GREATEST(20, ROUND((room.was * room.at_rate + room.got * ?) / (room.was + room.got)::numeric)) " +
            "      ELSE stomach_digest_ml_per_hour END, " +
            "  stomach_fullness_ml = stomach_fullness_ml + room.got, " +
            "  hours_without_food = 0, " +
            // Thirst too, for the water the food carried -- and only for the part that went down.
            "  hours_without_water = GREATEST(0, hours_without_water - CASE WHEN ? > 0 " +
            "      THEN (? * room.got / ?::numeric) / 50 ELSE 0 END), " +
            "  energy_level = LEAST(100, energy_level + ?), " +
            "  stress_level = GREATEST(0, stress_level - ?) " +
            " FROM room WHERE chronicle_id = ?",
            bulk, STOMACH_CAPACITY_ML, chronicleId,
            bulk, offeredKcal, bulk,
            Math.max(20, rate),
            bulk, Math.max(0, waterMl), bulk,
            Math.min(8, offeredKcal / 140), stressRelief, chronicleId);
        refreshBody(chronicleId);
    }
    public void eatCookedMeal(UUID chronicleId) { eatCookedMeal(chronicleId, com.devosphere.draugr.quality.QualityGrade.SOUND); }
    @Transactional
    /** A prepared meal: a bigger portion, and it settles the nerves as it always did (#959). */
    public void eatCookedMeal(UUID chronicleId, com.devosphere.draugr.quality.QualityGrade grade) {
        eatPortion(chronicleId, COOKED_MEAL_ML, grade, 2);
    }
    /** SOUND is the baseline (×1.0); a defective meal nourishes a little less, a fine one a little more. */
    private static double nourishmentFactor(com.devosphere.draugr.quality.QualityGrade grade) {
        return switch (grade == null ? com.devosphere.draugr.quality.QualityGrade.SOUND : grade) {
            case DEFECTIVE -> 0.75; case POOR -> 0.90; case SOUND -> 1.00; case FINE -> 1.20; };
    }
    @Transactional
    public void drink(UUID chronicleId) { drink(chronicleId, null); }
    /**
     * Drink. Ten hours of thirst eased is what this has always been worth, and at 50 ml to the hour that
     * is a 500 ml draught -- which is exactly what every water item in the catalogue holds. So when the
     * drink is a NAMED thing the amount comes off its own water content (#962) and the flat ten hours is
     * kept only for drinking straight from a stream, where there is no vessel to ask.
     */
    @Transactional
    public void drink(UUID chronicleId, String itemKey) {
        Double ml = itemKey == null ? null : jdbc.query(
            "SELECT d.unit_mass_grams * f.water_percent / 100.0 FROM item_definition d " +
            "  JOIN food_nourishment f ON f.item_key = d.item_key WHERE d.item_key = ?",
            rs -> rs.next() ? rs.getDouble(1) : null, itemKey);
        double hours = ml == null ? 10 : ml / 50.0;
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=GREATEST(0,hours_without_water-?) WHERE chronicle_id=?",
            java.math.BigDecimal.valueOf(hours).setScale(2, java.math.RoundingMode.HALF_UP), chronicleId);
        refreshBody(chronicleId);
    }
    /** Gut-illness load from drinking untreated water (#71/#59): a small rise in illness that accumulates —
     *  drinking raw or from a standing source repeatedly is how a Chronicle sickens. Boiled water carries none. */
    @Transactional
    public void applyWaterborneRisk(UUID chronicleId, int severity) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=LEAST(100,illness_severity+?) WHERE chronicle_id=?", Math.max(0, severity), chronicleId);
        refreshBody(chronicleId);
    }
    @Transactional
    public void wash(UUID chronicleId) { wash(chronicleId, false); }
    /** Wash in reachable water (#66). Soap (V89) lifts far more dirt than water alone and settles the mind more. */
    @Transactional
    public void wash(UUID chronicleId, boolean withSoap) {
        int hygiene = withSoap ? 45 : 28, stress = withSoap ? 6 : 4;
        jdbc.update("UPDATE chronicle_physiology SET hygiene_level=LEAST(100,hygiene_level+?),wetness_level=LEAST(100,wetness_level+18),stress_level=GREATEST(0,stress_level-?) WHERE chronicle_id=?", hygiene, stress, chronicleId);
        refreshBody(chronicleId);
    }
    /** A bare-hand cover placed here (#195): a windbreak or brush screen keeps the wind off, so a fire warms
     *  better and rest out of the wind eases the body more. Not shelter — it turns no rain and has no roof. */
    private boolean windbreakAt(UUID chronicleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id JOIN world_object body ON body.current_location_id=w.current_location_id WHERE body.id=? AND cp.project_kind='WINDBREAK' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND w.lifecycle_state='ACTIVE')", Boolean.class, chronicleId));
    }
    /** Warm by a fire in reach (#66): core temperature climbs toward normal, and a little wet steams off. A
     *  windbreak (#195) keeps the wind from stealing the heat, so more of it reaches the body. */
    /** A ring of stones set round the fire (#195): it holds the embers and throws their heat back, so a little
     *  more of the fire's warmth reaches the body. Not a hearth — just gathered cobbles, but they help. */
    private boolean stoneRingAt(UUID chronicleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object r ON r.id=cp.object_id JOIN world_object body ON body.current_location_id=r.current_location_id WHERE body.id=? AND cp.project_kind='STONE_RING' AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND r.lifecycle_state='ACTIVE')", Boolean.class, chronicleId));
    }
    @Transactional
    public void warmByFire(UUID chronicleId) {
        boolean wind = windbreakAt(chronicleId);
        boolean ring = stoneRingAt(chronicleId);
        double tempGain = (wind ? 1.0 : 0.7) + (ring ? 0.2 : 0.0); int wetLoss = wind ? 16 : 12;
        jdbc.update("UPDATE chronicle_physiology SET core_temperature_c=LEAST(37.5, core_temperature_c+?), wetness_level=GREATEST(0,wetness_level-?), stress_level=GREATEST(0,stress_level-3) WHERE chronicle_id=?", tempGain, wetLoss, chronicleId);
        refreshBody(chronicleId);
    }
    /** Drink a hot herbal infusion (#54 cooking): the warmth spreads from within, the steeped herb (chamomile,
     *  mint, elder) settles the mind, and the cupful quenches a little. A small warming, calming, and partial
     *  hydration that eating it as plain food never gave — milder than a fire, for a cup is not a hearth. */
    @Transactional
    /**
     * ONLY THE WARMTH NOW (#962). A hot infusion carried three effects, and two of them -- the water in it
     * and the calm the steeped herb brings -- are properties of the drink, so they are declared on it in
     * {@code food_nourishment} (300 ml of water, and the strongest comfort of any food in the catalogue)
     * and applied by {@link #eatOneOf}. Leaving them here as well would pay for the same cup twice, which
     * is the nourishment duplication #962 asks to be verified against. What is left is what is genuinely
     * about the HEAT rather than the liquid: a cup of something hot warms a cold body.
     */
    public void drinkWarmInfusion(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET core_temperature_c=LEAST(37.3, core_temperature_c+0.4) " +
                " WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** Dry off by a fire or under cover (#66): wetness falls markedly. */
    @Transactional
    public void dryOff(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET wetness_level=GREATEST(0,wetness_level-30), stress_level=GREATEST(0,stress_level-2) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** Cool off in shade or water (#66): core temperature eases down toward normal when overheated. */
    @Transactional
    public void coolOff(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET core_temperature_c=GREATEST(36.5, core_temperature_c-0.7), stress_level=GREATEST(0,stress_level-2) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** Get under cover out of the weather (#66): a little drying and an easing of stress. */
    @Transactional
    public void shelterFromWeather(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET wetness_level=GREATEST(0,wetness_level-15), stress_level=GREATEST(0,stress_level-4) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** Stretch and loosen the limbs (#66): a small easing of stiffness and stress. */
    @Transactional
    public void stretch(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET stress_level=GREATEST(0,stress_level-5), pain_level=GREATEST(0,pain_level-2) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /** The small easing of mind that comes of setting a camp in order (#71 maintain_camp). */
    @Transactional
    public void settleCamp(UUID chronicleId) {
        jdbc.update("UPDATE chronicle_physiology SET stress_level=GREATEST(0,stress_level-6) WHERE chronicle_id=?", chronicleId);
        refreshBody(chronicleId);
    }
    /**
     * A bed the chronicle can lie on here (#71): a laid pallet or a raised platform, off the cold, wet ground.
     * Not shelter — it turns no weather — but it makes rest and even exposed sleep markedly less broken.
     */
    /**
     * Is there somewhere to sleep on this ground that is not the ground (#919)?
     *
     * <p>A bark/grass groundsheet (#195) is the humblest of these — it turns no weather, but like any bed it
     * gets the sleeper off the cold, wet earth, so it counts here for rest and drying.
     *
     * <p><b>THE CATALOGUE SAYS WHICH, NOT A LIST IN THIS FILE.</b> This named three project kinds, and the
     * world had grown two more it never learned: a Chronicle who built a RAISED_BED_PLATFORM or a
     * SLEEPING_BENCH — both catalogued, both buildable — lay down on it and was warmed by nothing, because
     * the literals could not grow when the catalogue did. V470 puts the answer in {@code is_bedding} and this
     * asks for it, so the next bed anyone adds works the day it is catalogued.
     */
    private boolean beddedAt(UUID chronicleId) { return comfortOfTheBedAt(chronicleId) > 0; }

    /**
     * How good a night the best bedding on this ground makes, or 0 for the bare earth (#919).
     *
     * <p>V470 made every catalogued bedding count and left them all worth the same, so a Chronicle who
     * raised a platform slept no better than one who threw down a bark sheet. The grade is the catalogue's
     * ({@code bedding_comfort}: 1 barely off the wet ground, 2 a thick dry pallet, 3 up off the ground
     * altogether) and the BEST one on the ground is the one you lie on — nobody sleeps on the worse of two
     * beds they own.
     *
     * <p>Unfinished, ruined and destroyed bedding answers 0 by the same conditions that have always applied:
     * the project must be COMPLETED, its integrity above nothing, and the thing itself still standing.
     */
    private int comfortOfTheBedAt(UUID chronicleId) {
        Integer best = jdbc.queryForObject(
            "SELECT COALESCE(MAX(ck.bedding_comfort), 0) FROM construction_project cp " +
            "JOIN construction_kind ck ON ck.project_kind = cp.project_kind " +
            "JOIN world_object bed ON bed.id = cp.object_id " +
            "JOIN world_object body ON body.current_location_id = bed.current_location_id " +
            "WHERE body.id = ? AND ck.is_bedding AND cp.state = 'COMPLETED' " +
            "  AND cp.integrity_percent > 0 AND bed.lifecycle_state = 'ACTIVE'", Integer.class, chronicleId);
        return best == null ? 0 : best;
    }
    /**
     * Whether a built seat — the wooden chair a Chronicle can craft (CRAFT_CHAIR) — stands on this ground. A seat
     * eases a sit-down rest more than the bare earth does, the humblest of camp comforts; it gives the built chair
     * something to do, where it read against nothing before. Sited at a location like other furniture, reachable
     * there.
     */
    private boolean seatedAt(UUID chronicleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM world_object chair JOIN item_instance i ON i.object_id=chair.id JOIN world_object body ON body.current_location_id=chair.current_location_id WHERE body.id=? AND i.item_key='wooden_chair' AND chair.lifecycle_state='ACTIVE')", Boolean.class, chronicleId));
    }
    @Transactional
    public void rest(UUID chronicleId, int minutes) {
        double hours = minutes / 60.0;
        Boolean sheltered = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object shelter ON shelter.id=cp.object_id JOIN world_object body ON body.current_location_id=shelter.current_location_id WHERE body.id=? AND " + com.devosphere.draugr.construction.Shelters.ENCLOSING + " AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND shelter.lifecycle_state='ACTIVE') OR " + com.devosphere.draugr.construction.Shelters.lentByAPeople("?"), Boolean.class, chronicleId, chronicleId);
        boolean bed = beddedAt(chronicleId);
        // A rest recovers best under a shelter, next in a bed off the cold ground, next on a proper seat, and least
        // on the bare earth. A chair is a modest comfort — better than the ground, less than lying down — and only
        // matters when nothing better is to hand (you would not sit up in a chair when a bed or shelter is there).
        double recovery = Boolean.TRUE.equals(sheltered) ? 1.25 : (bed ? 1.12 : (seatedAt(chronicleId) ? 1.06 : 1.0));
        int drying = Boolean.TRUE.equals(sheltered) ? Math.max(1, (int)Math.round(hours * 8)) : (bed ? Math.max(1, (int)Math.round(hours * 3)) : 0);
        jdbc.update("UPDATE chronicle_physiology SET sleep_debt_hours=GREATEST(0,sleep_debt_hours-?),energy_level=LEAST(100,energy_level+?),pain_level=GREATEST(0,pain_level-?),stress_level=GREATEST(0,stress_level-?),wetness_level=GREATEST(0,wetness_level-?) WHERE chronicle_id=?", hours * .85 * recovery, Math.max(1, (int)Math.round(hours * 9 * recovery)), Math.max(0, (int)Math.round(hours * recovery)), Math.max(0, (int)Math.round(hours * 2 * recovery)), drying, chronicleId);
        refreshBody(chronicleId);
    }
    /** Deep sleep — a fuller recovery than a brief rest. Shelter makes it restful; sleeping exposed is possible but shallow. Returns whether the chronicle was sheltered. */
    @Transactional
    /**
     * What a sleep was slept on and under (#893) — so the sentence that reports it can say.
     *
     * <p>{@code sleep} returned a bare "was it sheltered", and the narration had nothing else to go on: a
     * Chronicle who had gathered reeds, laid a pallet and lain down on it at an open camp was told they
     * settled onto the bare ground. The bed was in the recovery arithmetic the whole time and in none of
     * the words.
     */
    public record Slept(boolean sheltered, boolean bedded) { }

    public Slept sleep(UUID chronicleId, int minutes) {
        double hours = minutes / 60.0;
        Boolean sheltered = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object shelter ON shelter.id=cp.object_id JOIN world_object body ON body.current_location_id=shelter.current_location_id WHERE body.id=? AND " + com.devosphere.draugr.construction.Shelters.ENCLOSING + " AND cp.state='COMPLETED' AND cp.integrity_percent>0 AND shelter.lifecycle_state='ACTIVE') OR " + com.devosphere.draugr.construction.Shelters.lentByAPeople("?"), Boolean.class, chronicleId, chronicleId);
        boolean safe = Boolean.TRUE.equals(sheltered);
        int comfort = comfortOfTheBedAt(chronicleId);
        boolean bed = comfort > 0;
        // Sheltered sleep is deep and restorative; a bed off the cold, wet ground makes exposed sleep far
        // less broken than lying on bare earth, though it turns no weather.
        //
        // THE TWO ARE SEPARATE GOODS AND USED TO BE ONE CHOICE (#919). This read
        // `safe ? 1.5 : (bed ? 1.0 : 0.7)`, so the moment a roof was overhead the bed beneath counted for
        // nothing: a raised bed inside a debris hut rested a body exactly as well as its bare floor did,
        // and nothing a player built to sleep on could show itself. A roof turns the weather and a bed
        // keeps the ground's cold out of you — neither does the other's work.
        //
        // The three combinations that existed before keep the numbers they had, so nothing a player has
        // already felt changes; only the fourth — a bed UNDER cover, which was unreachable — is new.
        // ONE BED IS BETTER THAN ANOTHER (#919). A groundsheet is barely off the mud and a built platform is
        // off the ground altogether, and until V471 both bought exactly the same night. The middle grade
        // keeps the 0.3 it shipped with, so nothing a player has already slept on changes.
        double recovery = 0.7 + (safe ? 0.8 : 0) + (bed ? 0.2 + 0.05 * comfort : 0);
        int drying = (safe ? Math.max(1, (int) Math.round(hours * 9)) : 0)
                   + (bed ? Math.max(1, (int) Math.round(hours * (safe ? 3 : 4))) : 0);
        // THE COLD COMES UP OUT OF THE GROUND, and a bed is what closes that path (#919). The metabolic tick
        // models the air, the wind and what is worn; the ground it has never modelled at all, so a raised bed
        // inside a hut kept a body no warmer than the hut's bare floor. This is the one place a bed is
        // unambiguously IN USE — a body asleep on it for the whole span — so the term lives here rather than
        // in the tick, where a bed at camp would be insulating a Chronicle who is out chopping wood.
        //
        // It only ever warms toward 37 and never past it: a body already warm gains nothing, and a cold one
        // climbs out faster on a better bed. Bounded at two thirds of a degree a night, because a bed is a
        // bed and not a fire.
        double groundWarmth = Math.min(0.66, hours * 0.02 * comfort);
        jdbc.update("UPDATE chronicle_physiology SET sleep_debt_hours=GREATEST(0,sleep_debt_hours-?),energy_level=LEAST(100,energy_level+?),pain_level=GREATEST(0,pain_level-?),stress_level=GREATEST(0,stress_level-?),wetness_level=GREATEST(0,wetness_level-?),core_temperature_c=LEAST(37.0,core_temperature_c+?) WHERE chronicle_id=?", hours * 1.1 * recovery, Math.max(1, (int)Math.round(hours * 12 * recovery)), Math.max(0, (int)Math.round(hours * 1.5 * recovery)), Math.max(0, (int)Math.round(hours * 3 * recovery)), drying, java.math.BigDecimal.valueOf(groundWarmth), chronicleId);
        refreshBody(chronicleId);
        return new Slept(safe, bed);
    }
    /**
     * WHERE AN INJURY LANDED (#37). The ticket's complaint was that "how is my leg" and "how are my feet"
     * reached nothing, and the diagnosis was that nothing in this world recorded where a wound was: injury
     * severity, pain and blood loss are single scalars over the whole body.
     *
     * <p><b>No new table and no new healing model.</b> Every injury already writes an immutable
     * {@code chronicle_condition_event} carrying its severity, its time and a {@code source} in a jsonb
     * payload — the history was already there and simply did not say where. So the part goes in that payload,
     * and the SCALARS stay the one authority on whether the body is hurt and how much. A per-limb severity
     * would have been a second truth about the same wound, and this project has paid for those.
     *
     * <p>The parts are the words a person uses about their own body. Left and right are deliberately absent:
     * nothing in the simulation distinguishes a Chronicle's sides, and a payload that said LEFT_LEG would be
     * claiming knowledge the world does not have.
     */
    public static final java.util.Set<String> BODY_PARTS = java.util.Set.of(
        "HEAD", "FACE", "SHOULDER", "ARM", "HAND", "TORSO", "BACK", "HIP", "LEG", "KNEE", "FOOT", "ANKLE");

    /**
     * Which part an animal's attack reaches, read from the CATALOGUE's own size tier rather than invented.
     *
     * <p>This is the same move {@code base_resistance} made for how badly a wound hurts: the answer was already
     * in the species row. A thing that lives at ankle height strikes the ankle; a thing that meets you at the
     * waist takes the arm you put up; a thing that stands taller than you reaches your chest. A beast you had
     * your hands on takes the hand, whatever its size, because that is where your hands were.
     *
     * <p>Deterministic on purpose. A random site would make the same bite land somewhere different each run,
     * which is untestable and tells the player nothing they can reason about.
     */
    public static String whereAnAnimalReaches(String sizeTier, boolean hadHandsOnIt) {
        if (hadHandsOnIt) return "HAND";
        if (sizeTier == null) return null;
        return switch (sizeTier.toUpperCase(java.util.Locale.ROOT)) {
            case "TINY", "SMALL" -> "ANKLE";
            case "MEDIUM" -> "ARM";
            case "LARGE", "HUGE" -> "TORSO";
            default -> null;
        };
    }

    @Transactional
    public void applyInjury(UUID chronicleId, int severity, UUID actionId, Instant occurredAt, String source) {
        applyInjury(chronicleId, severity, actionId, occurredAt, source, null);
    }

    /**
     * As above, recording WHERE it landed.
     *
     * <p>A null part records none rather than inventing one. An injury whose site the world genuinely does not
     * know — and there are such — leaves the body hurt without a place, and the reading says exactly that
     * instead of guessing a limb.
     */
    @Transactional
    public void applyInjury(UUID chronicleId, int severity, UUID actionId, Instant occurredAt, String source, String bodyPart) {
        int bounded=clamp(severity);
        String part = bodyPart == null ? null : bodyPart.toUpperCase(java.util.Locale.ROOT);
        if (part != null && !BODY_PARTS.contains(part)) part = null;
        jdbc.update("UPDATE chronicle_physiology SET injury_severity=LEAST(100,injury_severity+?),pain_level=LEAST(100,pain_level+?),stress_level=LEAST(100,stress_level+?),blood_loss_ml=LEAST(7000,blood_loss_ml+?) WHERE chronicle_id=?",bounded,Math.max(1,bounded/2),Math.max(1,bounded/3),bounded*8,chronicleId);
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('source',?::text,'part',?::text))",chronicleId,Timestamp.from(occurredAt),"INJURY",bounded,actionId,source,part);
        refreshBody(chronicleId);
    }

    /**
     * Where this body is hurt, worst first — the reading behind "how is my leg" and "where am I hurt" (#37).
     *
     * <p>Gated on the body still CARRYING an injury, because the events are permanent history and a cut that
     * healed a month ago is not an answer to "how is my leg". While {@code injury_severity} is above zero the
     * recent sites are what it is made of; once it is zero the body is whole and says so, however much history
     * it has. That is why there is no per-part healing: the scalar already knows.
     */
    /**
     * Whether this body is carrying an injury at all — asked of the SCALAR, which is the authority (#37).
     *
     * <p>The reading that needs this first asked the Body HUD's {@code health} label instead, and that label is
     * DERIVED: it is refreshed from the scalars and can be a step behind them. One authority for one fact, or
     * the two drift and the prose confidently says a body is whole while its injury severity says otherwise.
     */
    /**
     * What is actually wrong with this body, as numbers (#886, #914, #921).
     *
     * <p>The Body HUD's one-word {@code health} is DERIVED from these three, and every reader that asked the
     * label instead of the scalars inherited whatever the label could not say. That is this project's
     * recurring defect, and it produced the plainest contradiction a player has reported: "am I sick?"
     * answering with an unclosed hurt, and "bandage my wound" answering, correctly, that nothing was open.
     *
     * @param injury    tissue damage, 0-100 — a cut, a bite, or an accumulation of strains
     * @param illness   sickness, 0-100 — above 80 is Critical and 100 is Systemic Illness
     * @param bloodLoss millilitres lost and not yet made up
     * @param illnessTrend what the last tick did to the illness: positive is worsening, negative is mending,
     *                     zero is holding. Written by {@link #advanceTo}, because that is the only code that
     *                     sees the whole of what drives it — the filth, the wet and the cold, the wound that
     *                     will not close, the unvented hearth, the refuse at the camp.
     */
    public record Ailments(int injury, int illness, int bloodLoss, int illnessTrend) { }

    @Transactional(readOnly = true)
    public Ailments ailments(UUID chronicleId) {
        Ailments none = new Ailments(0, 0, 0, 0);
        if (chronicleId == null) return none;
        Ailments read = jdbc.query(
            "SELECT injury_severity, illness_severity, blood_loss_ml, illness_trend FROM chronicle_physiology WHERE chronicle_id=?",
            rs -> rs.next() ? new Ailments(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)) : null, chronicleId);
        return read == null ? none : read;
    }

    @Transactional(readOnly = true)
    public boolean carriesAnInjury(UUID chronicleId) {
        Integer severity = jdbc.queryForObject(
            "SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicleId);
        return severity != null && severity > 0;
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> whereItHurts(UUID chronicleId, Instant now) {
        Integer carried = jdbc.queryForObject(
            "SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicleId);
        if (carried == null || carried <= 0) return java.util.List.of();
        return jdbc.queryForList(
            "SELECT payload->>'part' AS part, severity, occurred_at, payload->>'source' AS source "
            + "  FROM chronicle_condition_event "
            + " WHERE chronicle_id=? AND condition_kind='INJURY' AND payload->>'part' IS NOT NULL "
            + "   AND occurred_at > ?::timestamptz - interval '30 days' "
            + " ORDER BY severity DESC, occurred_at DESC LIMIT 6", chronicleId, Timestamp.from(now));
    }
    /**
     * A strain of overexertion (#217) — a pulled muscle, a wrenched back from driving the body past what it had
     * left. It hurts (pain is the main signal) and leaves a minor tissue injury that eases with rest and care, but
     * unlike a cut or a bite it does not bleed: no blood loss. A real recovery need, kept in history like any
     * condition, so there is no silent fatigue reset.
     */
    @Transactional
    public void applyStrain(UUID chronicleId, int severity, UUID actionId, Instant occurredAt) {
        int bounded = clamp(severity);
        jdbc.update("UPDATE chronicle_physiology SET injury_severity=LEAST(100,injury_severity+?),pain_level=LEAST(100,pain_level+?),stress_level=LEAST(100,stress_level+?) WHERE chronicle_id=?", Math.max(1, bounded / 2), bounded, Math.max(1, bounded / 3), chronicleId);
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('source','overexertion'))", chronicleId, Timestamp.from(occurredAt), "STRAIN", bounded, actionId);
        refreshBody(chronicleId);
    }
    @Transactional
    public void applyFoodborneIllness(UUID chronicleId, UUID actionId, Instant occurredAt) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=LEAST(100,illness_severity+12),stress_level=LEAST(100,stress_level+5) WHERE chronicle_id=?",chronicleId);
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('source','spoiled_food'))",chronicleId,Timestamp.from(occurredAt),"FOODBORNE_ILLNESS",12,actionId);
        refreshBody(chronicleId);
    }
    /** A general illness from any cause — venom, tainted water, a poisonous plant. Raises illness_severity and a little stress. */
    @Transactional
    public void applyIllness(UUID chronicleId, int severity, UUID actionId, Instant occurredAt, String source) {
        int bounded = clamp(severity);
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=LEAST(100,illness_severity+?),stress_level=LEAST(100,stress_level+?) WHERE chronicle_id=?", bounded, Math.max(1, bounded / 3), chronicleId);
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('source',?))", chronicleId, Timestamp.from(occurredAt), "ILLNESS", bounded, actionId, source);
        refreshBody(chronicleId);
    }
    /** Whether anything on this body is open or bleeding — so a refusal can say WHICH thing is missing (#37). */
    @Transactional(readOnly = true)
    public boolean hasOpenWound(UUID chronicleId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM chronicle_physiology WHERE chronicle_id=? AND (injury_severity>0 OR blood_loss_ml>0))",
            Boolean.class, chronicleId));
    }

    @Transactional
    public boolean bindWound(UUID chronicleId, PhysicalItemService items, UUID actionId, Instant occurredAt) {
        Integer wounded = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_physiology WHERE chronicle_id=? AND (injury_severity>0 OR blood_loss_ml>0)", Integer.class, chronicleId);
        if (wounded == null || wounded == 0) return false;
        // First aid grades by what the Chronicle can bring to bear (#125 Recovery). The binding: a medicinal
        // poultice (V87) staunches best; a clean rolled bandage (V119) next; a raw fibre binding last — at least
        // one is required. A bark splint and an arm sling (V119) are optional additions, each with its own effect:
        // a splint immobilises a break (the biggest cut to injury), a sling rests the limb (pain, and above all
        // stress). Poultice and bandage may both be applied — medicine dressed with a clean binding.
        boolean poultice = items.consumeOne(chronicleId, "herbal_poultice", occurredAt);
        boolean bandage  = items.consumeOne(chronicleId, "fibre_bandage_roll", occurredAt);
        boolean fibre    = (!poultice && !bandage) && items.consumeOne(chronicleId, "plant_fiber", occurredAt);
        if (!poultice && !bandage && !fibre) return false;
        boolean splint = items.consumeOne(chronicleId, "bark_splint_set", occurredAt);
        boolean sling  = items.consumeOne(chronicleId, "cordage_arm_sling", occurredAt);
        int blood  = (poultice ? 300 : 0) + (bandage ? 220 : 0) + (fibre ? 180 : 0);
        int pain   = (poultice ? 9 : 0) + (bandage ? 6 : 0) + (fibre ? 5 : 0) + (splint ? 7 : 0) + (sling ? 4 : 0);
        int injury = (poultice ? 4 : 0) + (bandage ? 2 : 0) + (splint ? 6 : 0) + (sling ? 1 : 0);
        int stress = 3 + (sling ? 3 : 0);
        jdbc.update("UPDATE chronicle_physiology SET blood_loss_ml=GREATEST(0,blood_loss_ml-?),pain_level=GREATEST(0,pain_level-?),stress_level=GREATEST(0,stress_level-?),injury_severity=GREATEST(0,injury_severity-?) WHERE chronicle_id=?", blood, pain, stress, injury, chronicleId);
        String method = (poultice ? "herbal_poultice" : bandage ? "fibre_bandage" : "plant_fiber_binding") + (splint ? "+splint" : "") + (sling ? "+sling" : "");
        jdbc.update("INSERT INTO chronicle_condition_event (chronicle_id,occurred_at,condition_kind,severity,source_action_id,payload) VALUES (?,?,?,?,?,jsonb_build_object('method',?))", chronicleId, Timestamp.from(occurredAt), "WOUND_BOUND", 1, actionId, method);
        refreshBody(chronicleId);
        return true;
    }
    public void refreshBody(UUID chronicleId) {
        jdbc.query("SELECT b.condition_summary,p.hours_without_food,p.hours_without_water,p.energy_level,p.core_temperature_c,p.wetness_level,p.bladder_level,p.bowel_level,p.hygiene_level,p.sleep_debt_hours,p.pain_level,p.stress_level,p.injury_severity,p.illness_severity,p.blood_loss_ml,p.stomach_fullness_ml,p.energy_reserves,p.nutrition,p.starvation_severity FROM chronicle_body b JOIN chronicle_physiology p ON p.chronicle_id=b.chronicle_id WHERE b.chronicle_id=?",rs->{if(rs.next()){Vitals v=vitalsOf(rs);String health=health(v);String condition=conditionSummary(v);
            // THE STORED ROW READS THE STOMACH TOO (#957/#959). Built from the ten-argument form it took its
            // Hunger from the CLOCK, so a Chronicle whose stomach was full read "Full" on screen and
            // "Satisfied" after a reload -- the two accounts of one body that #957 exists to stop.
            BodyHudSnapshot body=snapshot(v);jdbc.update("UPDATE chronicle_body SET health=?,condition_summary=?,hunger=?,thirst=?,energy=?,temperature=?,wetness=?,bladder=?,bowel=?,hygiene=? WHERE chronicle_id=?",health,condition,body.hunger(),body.thirst(),body.energy(),body.temperature(),body.wetness(),body.bladder(),body.bowel(),body.hygiene(),chronicleId);}return null;},chronicleId);
    }
    /**
     * A keeper's death ends the keeping, and the animals go on living (#108).
     *
     * <p>The death path has always been careful about THINGS — the possessions are set down on the ground where
     * the Chronicle fell, with their identity and history — and said nothing whatever about ANIMALS. Measured on
     * a booted world, killing a keeper of one tamed ox through this very path:
     *
     * <pre>
     *   before death          thirst 4,  hunger 3
     *   three turns later     thirst 16, hunger 12,  bond_stage still TAMED
     * </pre>
     *
     * <p>The ox went on getting thirstier and hungrier for ever, tamed to a corpse, and would have gone on
     * breeding, fouling the ground and wanting a byre nobody owned.
     *
     * <p>Eight of the ten husbandry passes filter on {@code bond_stage='TAMED'}, so ENDING THE KEEPING stops
     * all of them at once — which is why this is one statement rather than a filter added in eight places.
     * {@code keepsSuchABeast} already asked whether the keeper was LIVING, so the world's own convention was
     * there to follow.
     *
     * <p>The bond is NOT deleted. The animals were tamed, and that happened: the stage goes back to WILD (a
     * value the CHECK has always allowed) and {@code released_at} records when the keeping ended, so a later
     * cycle can tell a herd that was never tamed from one whose keeper died. {@code trust_level} is left where
     * it was, which is the honest basis for the next Chronicle taming the same animals: they knew a person
     * once. The POPULATION is untouched — the animals remain in the world, findable and tameable by whoever
     * comes after.
     */
    private void releaseTheirStock(UUID chronicle, Timestamp at) {
        jdbc.update("UPDATE wildlife_bond SET bond_stage='WILD', released_at=? " +
                    "WHERE chronicle_id=? AND released_at IS NULL", at, chronicle);
    }

    private void relocatePossessions(UUID chronicleId, UUID locationId, Instant occurredAt) {
        if (locationId == null) return;
        Timestamp occurred = Timestamp.from(occurredAt);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) SELECT id,?,'CHRONICLE_DIED_DROPPED',jsonb_build_object('locationId',?::text) FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", occurred, locationId.toString(), chronicleId);
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicleId);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL,current_location_id=?,updated_at=? WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", locationId, occurred, chronicleId);
    }

    /** The HUD from a body already read. Prefer {@link #snapshot(Vitals)}, which carries the real list. */
    public static BodyHudSnapshot snapshot(String health, String condition, double foodHours, double waterHours, int energy, double temperature, int wetness, int bladder, int bowel, int hygiene) {
        return new BodyHudSnapshot(health, condition, hungerFromHours(foodHours), thirst(waterHours), energy(energy), temperature(temperature), wetness(wetness), bladder(bladder), bowel(bowel), hygiene(hygiene));
    }
    /**
     * How well this body is being kept, 0-5 (#909).
     *
     * <p>The five things a person can actually DO about a sickness, and every one of them is already
     * simulated: get warm, get dry, get clean, eat and drink, sleep. Counted rather than weighted, because
     * the point is breadth — a Chronicle who has put right four of the five is looking after themselves and
     * a Chronicle who has put right one is not.
     */
    static int howWellKept(double core, int wetness, int hygiene, int reserves, int nutrition, int starvation,
                           double water, double sleepDebt) {
        return (core >= 36.0 ? 1 : 0) + (wetness < 40 ? 1 : 0) + (hygiene > 25 ? 1 : 0)
             + (isFedEnoughToMend(reserves, nutrition, starvation) && water < 24 ? 1 : 0) + (sleepDebt < 24 ? 1 : 0);
    }

    /**
     * FED, AS A BODY FIGHTING A SICKNESS MEANS IT (#962). This asked {@code hours_without_food < 168} -- had
     * anything been eaten this week -- so a body living on watercress mended exactly as well as one living on
     * meat, and a mouthful of leaves after six days of nothing counted the same as a week of proper meals.
     *
     * <p>Three things the tick already keeps, each the reason the clock was the wrong question:
     * <ul>
     *   <li>{@code reserves > 0} -- there is something to burn now. An empty store is a body spending itself,
     *       with nothing to spare for a fever.</li>
     *   <li>{@code nutrition >= 40} -- it has been SUSTAINED. Nutrition follows the energy density of what has
     *       gone in over weeks, so a diet of greens walks it down and one good meal does not walk it back.</li>
     *   <li>{@code starvation < 55} -- not still taking itself apart. 55 is the same line thermoregulation draws
     *       for a body too damaged to keep itself warm.</li>
     * </ul>
     */
    static boolean isFedEnoughToMend(int reserves, int nutrition, int starvation) {
        return reserves > 0 && nutrition >= 40 && starvation < 55;
    }

    /**
     * How much illness a well-kept body throws off in this many hours (#909).
     *
     * <p>Nothing below three, because a body half looked-after holds its ground and no more. At five it mends
     * at .6 an hour, which is the rate the worst single pressure above RAISES it — so a Chronicle doing
     * everything right exactly outruns the sickest camp they could have been living in. Deliberately slow:
     * climbing out of illness 99 takes about a day and a half of being properly kept, which is the point.
     *
     * <p>Pure and static so the rate is written once and can be read without a database or a world.
     */
    static int illnessMended(double hours, int howWellKept) {
        return howWellKept < 3 ? 0 : (int) Math.ceil(hours * .2 * (howWellKept - 2));
    }


    /**
     * The body, read out of the fifteen-column row both the HUD query and the stored-body refresh select
     * (#957). Written once because two readers of one body is how {@code health} came to see three numbers
     * while the death check saw seven.
     */
    private static Vitals vitalsOf(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Vitals(rs.getInt(13), rs.getInt(14), rs.getInt(15), rs.getBigDecimal(5).doubleValue(),
            rs.getBigDecimal(2).doubleValue(), rs.getBigDecimal(3).doubleValue(), rs.getInt(4),
            rs.getBigDecimal(10).doubleValue(), rs.getInt(6), rs.getInt(9), rs.getInt(7), rs.getInt(8),
            rs.getInt(11), rs.getInt(12),
            // columns 16-19 (#959): the stomach, the store, the diet and the damage.
            rs.getInt(16), rs.getInt(17), rs.getInt(18), rs.getInt(19));
    }
    /**
     * The hunger label for a caller that holds only the old clock, and nothing better to say.
     *
     * <p>Kept because {@code snapshot(...)}'s ten-argument form is called from twenty places that have a
     * number of hours and no stomach -- tests, the death snapshot of a pre-#959 Chronicle. The REAL path is
     * {@link #snapshot(Vitals)}, which has the stomach and overrides this with it.
     */
    private static String hungerFromHours(double hours) {
        return hours >= 720 ? "Critical Starvation" : hours >= 336 ? "Starving"
             : hours >= 168 ? "Very Hungry" : hours >= 72 ? "Hungry" : "Satisfied";
    }

    /**
     * HUNGER IS WHAT IS IN THE STOMACH (#959), which is what the word means and what a meal relieves.
     *
     * <p>It used to be the clock: 336 hours since eating read "Starving" however much had been eaten since,
     * because the counter a meal reduced by eight was the same counter the label was read from. So forty-two
     * berries read Satisfied and a whole roast fish after a fortnight read Starving. What going without has
     * actually DONE to a body is starvation_severity now, and it reports through Condition and Health --
     * where a Chronicle can be Critical with a full stomach, which is #956's own example.
     */
    private static String hunger(int fullnessMl) {
        return fullnessMl >= 900 ? "Full"
             : fullnessMl >= 500 ? "Satisfied"
             : fullnessMl >= 220 ? "Peckish"
             : fullnessMl >=  60 ? "Hungry"
             : "Empty";
    }
    private static String thirst(double hours) { return hours >= 72 ? "Critical Dehydration" : hours >= 48 ? "Dehydrated" : hours >= 24 ? "Thirsty" : "Hydrated"; }
    private static String energy(int value) { return value >= 90 ? "Energetic" : value >= 70 ? "Rested" : value >= 50 ? "Tired" : value >= 30 ? "Fatigued" : value >= 10 ? "Exhausted" : "Collapsing"; }
    private static String temperature(double value) { return value < 35 ? "Hypothermic" : value < 36.5 ? "Cold" : value <= 37.5 ? "Comfortable" : value <= 38.5 ? "Warm" : value <= 40 ? "Hot" : "Hyperthermic"; }
    private static String wetness(int value) { return value >= 75 ? "Soaked" : value >= 50 ? "Wet" : value >= 20 ? "Damp" : "Dry"; }
    private static String bladder(int value) { return value >= 90 ? "Critical" : value >= 70 ? "Urgent" : value >= 45 ? "Need to Urinate" : value <= 5 ? "Empty" : "Comfortable"; }
    private static String bowel(int value) { return value >= 90 ? "Critical" : value >= 70 ? "Urgent" : value >= 45 ? "Need Relief" : value <= 5 ? "Empty" : "Normal"; }
    private static String hygiene(int value) { return value >= 80 ? "Clean" : value >= 55 ? "Normal" : value >= 30 ? "Dirty" : value >= 10 ? "Filthy" : "Hazardous"; }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    /**
     * The one-word state of the body (#913).
     *
     * <p><b>A sickness is not a wound.</b> This returned "Injured" for either, so Chronicle 41 — illness 64,
     * injury 0, no blood lost — was labelled Injured, and every reader that trusted the label went on to
     * describe an open hurt to someone who had none. The player asked "am I sick?" and was told there was a
     * hurt in them that had not closed; they then asked to bandage it and were told nothing was open.
     *
     * <p>Four states now, because that is how many there are. "Ailing" is the honest word for both at once.
     */
    /**
     * EVERYTHING THE BODY'S STATE IS READ FROM (#957).
     *
     * <p>{@code health} saw three numbers — injury, illness, blood loss — while the death check twenty lines
     * away saw temperature, food and water as well. So a Chronicle at 29 °C core, four degrees from dying of
     * cold, read "Healthy", and every reader that trusted the label passed it on. One record now, so the
     * label and the death threshold cannot see different bodies.
     */
    public record Vitals(int injury, int illness, int bloodLoss, double core, double hoursWithoutFood,
                         double hoursWithoutWater, int energy, double sleepDebt, int wetness, int hygiene,
                         int bladder, int bowel, int pain, int stress,
                         int fullnessMl, int reserves, int nutrition, int starvation) {
        /**
         * The shape this record had before #959, kept so every existing caller and test still reads. A body
         * described without the nutrition four is described as an ordinarily fed one, which is what those
         * callers were implicitly asserting when there was nothing else to say.
         */
        public Vitals(int injury, int illness, int bloodLoss, double core, double hoursWithoutFood,
                      double hoursWithoutWater, int energy, double sleepDebt, int wetness, int hygiene,
                      int bladder, int bowel, int pain, int stress) {
            this(injury, illness, bloodLoss, core, hoursWithoutFood, hoursWithoutWater, energy, sleepDebt,
                 wetness, hygiene, bladder, bowel, pain, stress, FED_STOMACH, 70, 70, 0);
        }
    }

    /** A stomach holds about this much; a big meal fills it and anything more will not go in (#959). */
    public static final int STOMACH_CAPACITY_ML = 1200;
    /** What "an ordinary unremarkable amount in the stomach" means, for the compatibility constructor. */
    private static final int FED_STOMACH = 600;

    /**
     * How hard one system is pressing on this body, 0..100, where 100 is the threshold that kills, and the
     * severity from which it is worth NAMING.
     *
     * <p>Those are two different bars, and conflating them made the body contradict itself: the self-check
     * says "you are losing blood, and it is still coming" from 400 ml, while 900 ml is only a severity of
     * twenty — so the prose reported a wound the Condition list stayed silent about. A bleed is a FACT at a
     * low bar and a DANGER at a high one. #957 asks for consistency across the HUD and the self-assessment,
     * and this is where the two were disagreeing.
     */
    record Strain(int severity, String condition, int sayFrom) { }

    /** Below this a system is a deviation rather than a complaint, and goes unmentioned. */
    private static final int WORTH_SAYING = 25;
    /**
     * The medical complaints — a wound, a bleed, a sickness — are named as soon as they are real, because
     * the self-check already speaks of them at that point and the two must not disagree.
     */
    private static final int WORTH_SAYING_MEDICAL = 10;
    /**
     * The most a system that CANNOT kill may contribute. Exhaustion, filth, a full bladder and distress are
     * real and are felt, and none of them is a medical crisis — #957: "A minor deviation need not imply
     * medical crisis." Capping them below the Critical band is how that is guaranteed rather than hoped.
     */
    private static final int NOT_LETHAL = 55;

    private static int band(double value, double harmless, double fatal) {
        if (fatal == harmless) return 0;
        return (int) Math.max(0, Math.min(100, Math.round((value - harmless) / (fatal - harmless) * 100)));
    }

    private static String worst(int severity, String grave, String bad, String slight) {
        return severity >= 70 ? grave : severity >= 40 ? bad : slight;
    }

    /**
     * WHAT IS PRESSING ON THIS BODY, worst first (#957, #958).
     *
     * <p>Each system is measured against the threshold that actually kills, taken from the same constants
     * the death check uses: 73 hours without water, 721 without food, 3.5 litres of blood, 100 injury,
     * 28 °C and 42 °C core. A system at its threshold reads 100, so Health cannot say "Healthy" about a body
     * that is about to die of one thing while the rest of it is fine.
     */
    static java.util.List<Strain> strains(Vitals v) {
        java.util.List<Strain> all = new java.util.ArrayList<>();
        // The lethal systems, each able to reach 100 on its own.
        add(all, band(v.hoursWithoutWater(), 12, DEHYDRATION_DEATH_HOURS), "Dangerously dehydrated", "Dehydrated", "Thirsty");
        // THE HOURS-BASED STARVATION STRAIN IS GONE (#960). I wrote it in #957 because the clock was all
        // there was, and it carries the very defect #959 fixed: a Chronicle three hundred hours since their
        // last meal but with a full stomach and reserves read "Underfed" off the clock. starvation_severity
        // is the fact now, and it has its own strain a few lines down. One question, one answer.
        // STARVATION IS A LETHAL SYSTEM OF ITS OWN (#959/#960). It used to be read off the clock -- a
        // Chronicle was "Starving" at 336 hours whatever they had eaten since -- and it is its own measured
        // damage now, so a body can be Critical from it while the stomach is not empty.
        // Measured from nothing, not from a threshold: starvation_severity IS the distance travelled
        // toward death by it, so 40 of 100 is a body half way there and must not read as "going short".
        add(all, band(v.starvation(), 0, 100), "Starving", "Badly underfed", "Going short");
        // AND MALNOURISHMENT, WHICH DOES NOT KILL AND IS NOT NOTHING. #958's own example asks for
        // "Malnourished" beside a Hunger of Full, and it could not be given then because nothing modelled
        // it; nutrition does now, so the condition is derived rather than invented. Capped below Critical
        // because a poor diet makes you ill and weak over months, it does not kill you this afternoon --
        // starvation above is the thing that does.
        add(all, Math.min(NOT_LETHAL, band(100 - v.nutrition(), 45, 100)), "Severely malnourished", "Malnourished", "Poorly fed");
        // Spent reserves are felt as weakness long before starvation begins.
        add(all, Math.min(NOT_LETHAL, band(100 - v.reserves(), 70, 100)), "Running on nothing", "Weak with hunger", "Flagging");
        // The three the self-check speaks of, named from the moment they are real.
        medical(all, band(v.bloodLoss(), 250, 3500), "Bleeding badly", "Bleeding", "Grazed and bleeding");
        medical(all, band(v.injury(), 8, 100), "Badly injured", "Injured", "Hurt");
        medical(all, band(v.illness(), 8, 100), "Gravely sick", "Sick", "Unwell");
        add(all, band(v.core(), 36.5, 28.0), "Hypothermic", "Chilled through", "Cold");
        add(all, band(v.core(), 37.5, 42.0), "Dangerously overheated", "Overheating", "Too warm");
        // And the ones that are felt but do not kill, held below Critical by construction.
        add(all, Math.min(NOT_LETHAL, band(100 - v.energy(), 50, 100)), "Collapsing from exhaustion", "Exhausted", "Worn down");
        add(all, Math.min(NOT_LETHAL, band(v.sleepDebt(), 24, 72)), "Sleepless", "Sleep deprived", "Short of sleep");
        add(all, Math.min(NOT_LETHAL, band(v.pain(), 40, 100)), "In great pain", "In pain", "Aching");
        add(all, Math.min(NOT_LETHAL, band(v.stress(), 50, 100)), "Distraught", "Distressed", "Unsettled");
        add(all, Math.min(NOT_LETHAL, band(v.wetness(), 40, 100)), "Soaked through", "Soaked", "Damp and cooling");
        add(all, Math.min(NOT_LETHAL, band(100 - v.hygiene(), 60, 100)), "Filthy and at risk of infection", "Filthy", "Dirty");
        add(all, Math.min(NOT_LETHAL, band(Math.max(v.bladder(), v.bowel()), 70, 100)), "In urgent need of relief", "Needing relief", "Uncomfortable");
        all.sort((a, b) -> Integer.compare(b.severity(), a.severity()));
        return java.util.List.copyOf(all);
    }

    private static void add(java.util.List<Strain> all, int severity, String grave, String bad, String slight) {
        if (severity > 0) all.add(new Strain(severity, worst(severity, grave, bad, slight), WORTH_SAYING));
    }

    private static void medical(java.util.List<Strain> all, int severity, String grave, String bad, String slight) {
        if (severity > 0) all.add(new Strain(severity, worst(severity, grave, bad, slight), WORTH_SAYING_MEDICAL));
    }

    /**
     * THE AGGREGATE STATE OF THE BODY (#957).
     *
     * <p>The WORST system decides it, never an average: #957 asks that "a life-threatening system makes
     * overall Health Critical even if other systems are normal" and that an average must not conceal a
     * lethal condition. An average is exactly what would conceal it — one system at 95 and nine at 0
     * averages to a comfortable 9.5.
     *
     * <p>The words no longer name an ailment. "Injured" and "Sick" were doing two jobs — saying how bad the
     * body was AND what was wrong with it — and could only ever report one complaint. What is wrong is now
     * {@link #conditions}, which can report all of them; this says only how bad it is.
     */
    static String health(Vitals v) {
        int severity = strains(v).stream().mapToInt(Strain::severity).max().orElse(0);
        return severity >= 80 ? "Critical"
             : severity >= 60 ? "Failing"
             : severity >= 40 ? "Ailing"
             : severity >= 20 ? "Weakened"
             : "Healthy";
    }

    /**
     * EVERY COMPLAINT THIS BODY HAS, the most urgent first (#958).
     *
     * <p>Condition was one string whose precedence suppressed everything behind it: a Chronicle who was
     * injured, bleeding and sick was told one of those three and left to guess at the rest. Clearing one
     * complaint now leaves the others standing, because each is derived from its own system rather than
     * from a chain of if-returns.
     *
     * <p>Systems below {@link #WORTH_SAYING} are left out. A body is never perfectly comfortable and a list
     * that says so every tick is a list nobody reads.
     */
    static java.util.List<String> conditions(Vitals v) {
        return strains(v).stream().filter(s -> s.severity() >= s.sayFrom()).map(Strain::condition)
            .distinct().toList();
    }

    /** The complaints as one line, for the single-string field the HUD and the death snapshot already hold. */
    static String conditionSummary(Vitals v) {
        java.util.List<String> said = conditions(v);
        if (said.isEmpty()) return "Comfortable";
        // The stored line is chronicle_body.condition_summary, VARCHAR(100) since V4, and a body with
        // enough wrong with it at once says more than that: the latrine journey overflowed it and the
        // whole act failed on a DataIntegrityViolation. The LIST is never cut -- the HUD reads
        // conditions(v) whole. Only this one-line summary keeps the entries that fit, in the order
        // strains() ranks them, so the lethal ones lead and a complaint is dropped whole, never mid-word.
        StringBuilder line = new StringBuilder();
        for (String c : said) {
            int need = (line.length() == 0 ? 0 : 2) + c.length();
            if (line.length() + need > CONDITION_SUMMARY_WIDTH) break;
            if (line.length() > 0) line.append(", ");
            line.append(c);
        }
        if (line.length() == 0) return said.get(0).substring(0, Math.min(said.get(0).length(), CONDITION_SUMMARY_WIDTH));
        return line.toString();
    }
    /** chronicle_body.condition_summary is VARCHAR(100) (V4). */
    static final int CONDITION_SUMMARY_WIDTH = 100;
    private record Environment(String kind,int intensity,double ambient,int wind,int fuelMinutes,boolean shelter,boolean windbreak,boolean rainCover,boolean sunShade,boolean enclosed,boolean vented,boolean smokeWrap) { }
    /**
     * What the player is shown of their own body.
     *
     * <p>{@code conditions} is the list #958 asks for: Condition was one string whose precedence suppressed
     * everything behind it, so a Chronicle who was injured, bleeding and sick was told one of the three.
     * {@code condition} remains as those entries joined, because the stored body, the death snapshot and
     * twenty call sites all hold a single string, and a player reading one line should see the same
     * complaints as a player reading the list.
     *
     * <p>Nothing hidden appears here — no severity number, no conditioning, no proficiency. The entries are
     * words for what is wrong, which is what a person knows about their own body.
     */
    public record BodyHudSnapshot(String health, String condition, java.util.List<String> conditions,
                                  String hunger, String thirst, String energy, String temperature,
                                  String wetness, String bladder, String bowel, String hygiene) {
        /**
         * The shape this record had before #958, kept so every existing caller and test still reads. The
         * entries are recovered from the joined line, which is all such a caller has.
         */
        public BodyHudSnapshot(String health, String condition, String hunger, String thirst, String energy,
                              String temperature, String wetness, String bladder, String bowel, String hygiene) {
            this(health, condition, splitConditions(condition), hunger, thirst, energy, temperature, wetness,
                 bladder, bowel, hygiene);
        }

        private static java.util.List<String> splitConditions(String joined) {
            if (joined == null || joined.isBlank() || "Comfortable".equals(joined)) return java.util.List.of();
            return java.util.Arrays.stream(joined.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    /**
     * The HUD for a body, with its complaints as the list they are (#958) rather than a line to be split
     * apart again later.
     */
    public static BodyHudSnapshot snapshot(Vitals v) {
        BodyHudSnapshot plain = snapshot(health(v), conditionSummary(v), v.hoursWithoutFood(),
            v.hoursWithoutWater(), v.energy(), v.core(), v.wetness(), v.bladder(), v.bowel(), v.hygiene());
        // Hunger is overridden with the stomach (#959). The ten-argument form above only had the clock, and
        // the clock is history now rather than the thing a meal has to erase.
        return new BodyHudSnapshot(plain.health(), plain.condition(), conditions(v), hunger(v.fullnessMl()),
            plain.thirst(), plain.energy(), plain.temperature(), plain.wetness(), plain.bladder(),
            plain.bowel(), plain.hygiene());
    }
}
