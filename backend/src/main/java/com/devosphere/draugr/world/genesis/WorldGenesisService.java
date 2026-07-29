package com.devosphere.draugr.world.genesis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorldGenesisService {
    private static final String GENERATOR_VERSION = "mvp-1";
    private final JdbcTemplate jdbc;
    private final Path exportDirectory;

    public WorldGenesisService(JdbcTemplate jdbc, @Value("${draugr.overseer.export-directory:../world-exports}") String exportDirectory) {
        this.jdbc = jdbc;
        this.exportDirectory = Path.of(exportDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public GenesisSummary generate(GenesisRequest request) {
        validateDimensions(request);
        List<UUID> existing = jdbc.query("SELECT world_id FROM world_genesis", (rs, row) -> UUID.fromString(rs.getString(1)));
        if (!existing.isEmpty()) throw new IllegalStateException("Canonical world already exists; World Genesis may run only once.");
        UUID worldId = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'WORLD_ROOT', 'Draugr Canonical World', ?)", worldId, worldId);
        jdbc.update("INSERT INTO world_genesis (world_id, seed, generator_version, width_chunks, height_chunks) VALUES (?, ?, ?, ?, ?)", worldId, request.seed(), GENERATOR_VERSION, request.widthChunks(), request.heightChunks());
        Map<String, Integer> biomes = persistChunks(worldId, request);
        Path preview = writePreview(request, "overseer-map.png");
        return new GenesisSummary(worldId, request.seed(), request.widthChunks(), request.heightChunks(), Map.copyOf(biomes), preview.toString(), Instant.now());
    }

    public PreviewSummary preview(GenesisRequest request) {
        validateDimensions(request);
        Map<String, Integer> biomes = countBiomes(request);
        Path preview = writePreview(request, "overseer-preview-" + request.seed() + ".png");
        return new PreviewSummary(request.seed(), request.widthChunks(), request.heightChunks(), Map.copyOf(biomes), markersFor(request), preview.toString());
    }

    public Path previewImage(GenesisRequest request) {
        preview(request);
        return exportDirectory.resolve("overseer-preview-" + request.seed() + ".png");
    }

    @Transactional(readOnly = true)
    public GenesisSummary current() {
        return jdbc.query("SELECT world_id, seed, width_chunks, height_chunks, generated_at FROM world_genesis", rs -> {
            if (!rs.next()) return null;
            UUID worldId = UUID.fromString(rs.getString("world_id"));
            Map<String, Integer> biomes = new LinkedHashMap<>();
            jdbc.queryForList("SELECT biome, COUNT(*) AS count FROM world_chunk WHERE world_id = ? GROUP BY biome ORDER BY biome", worldId)
                    .forEach(row -> biomes.put((String) row.get("biome"), ((Number) row.get("count")).intValue()));
            return new GenesisSummary(worldId, rs.getLong("seed"), rs.getInt("width_chunks"), rs.getInt("height_chunks"), Map.copyOf(biomes), previewPath().toString(), rs.getTimestamp("generated_at").toInstant());
        });
    }

    private Map<String, Integer> persistChunks(UUID worldId, GenesisRequest request) {
        Map<String, Integer> biomes = new LinkedHashMap<>();
        for (int y = 0; y < request.heightChunks(); y++) for (int x = 0; x < request.widthChunks(); x++) {
            TerrainCell cell = terrainAt(x, y, request.widthChunks(), request.heightChunks(), request.seed(), true);
            UUID chunkId = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id, object_type, display_name, current_location_id) VALUES (?, 'GEOGRAPHIC_CHUNK', ?, ?)", chunkId, "Uncharted terrain " + x + "," + y, worldId);
            jdbc.update("INSERT INTO world_chunk (id, world_id, grid_x, grid_y, elevation, moisture, biome) VALUES (?, ?, ?, ?, ?, ?, ?)", chunkId, worldId, x, y, cell.elevation(), cell.moisture(), cell.biome());
            biomes.merge(cell.biome(), 1, Integer::sum);
        }
        return biomes;
    }

    private Map<String, Integer> countBiomes(GenesisRequest request) {
        Map<String, Integer> biomes = new LinkedHashMap<>();
        for (int y = 0; y < request.heightChunks(); y++) for (int x = 0; x < request.widthChunks(); x++)
            biomes.merge(terrainAt(x, y, request.widthChunks(), request.heightChunks(), request.seed(), true).biome(), 1, Integer::sum);
        return biomes;
    }

    private void validateDimensions(GenesisRequest request) {
        if (request.widthChunks() <= 0 || request.heightChunks() <= 0) throw new IllegalArgumentException("World dimensions must be positive.");
        if (request.widthChunks() > 64 || request.heightChunks() > 64) throw new IllegalArgumentException("MVP Overseer preview supports up to 64 chunks per axis.");
    }

    private Path writePreview(GenesisRequest request, String fileName) {
        int imageWidth = 1600, mapHeight = 1040, header = 110;
        BufferedImage image = new BufferedImage(imageWidth, mapHeight + header, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(18, 24, 22)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int py = 0; py < mapHeight; py++) for (int px = 0; px < imageWidth; px++) {
            TerrainCell cell = terrainAt(((double) px / imageWidth) * (request.widthChunks() - 1), ((double) py / mapHeight) * (request.heightChunks() - 1), request.widthChunks(), request.heightChunks(), request.seed(), false);
            g.setColor(shade(colorFor(cell.biome()), cell.elevation())); g.fillRect(px, header + py, 1, 1);
        }
        g.setColor(new Color(236, 232, 216)); g.setFont(new Font(Font.SERIF, Font.PLAIN, 26)); g.drawString("PROJECT DRAUGR — OVERSEER MAP", 28, 39);
        g.setColor(new Color(181, 194, 180)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14)); g.drawString("World seed " + request.seed() + "  •  Geography and ecological placement preview", 30, 67);
        g.setStroke(new BasicStroke(1.5f)); g.setColor(new Color(230, 226, 209, 110)); g.drawLine(28, 84, 1572, 84);
        for (PreviewMarker marker : markersFor(request)) drawMarker(g, marker, imageWidth, mapHeight, header, request);
        drawLegend(g, imageWidth); g.dispose();
        try { Files.createDirectories(exportDirectory); Path path = exportDirectory.resolve(fileName); ImageIO.write(image, "png", path.toFile()); return path; }
        catch (IOException exception) { throw new IllegalStateException("Unable to write Overseer map preview", exception); }
    }

    private TerrainCell terrainAt(double x, double y, int width, int height, long seed, boolean localVariation) {
        double nx = x / Math.max(1, width - 1), ny = y / Math.max(1, height - 1);
        double variation = localVariation ? deterministicVariation((int) Math.round(x), (int) Math.round(y), seed) : 0;
        double elevation = 0.49 + 0.22 * Math.sin(nx * 7.0 + seed * 0.00000003) + 0.16 * Math.cos(ny * 8.0) + 0.09 * Math.sin((nx + ny) * 15.0) + variation * 0.10;
        double moisture = 0.54 + 0.20 * Math.cos(nx * 8.0 - ny * 3.0) + variation * 0.14;
        int elevationValue = (int) Math.round(Math.clamp(elevation, 0, 1) * 1000), moistureValue = (int) Math.round(Math.clamp(moisture, 0, 1) * 1000);
        String biome = elevation < 0.30 ? "OCEAN" : elevation < 0.38 ? "WETLAND" : elevation > 0.82 ? "MOUNTAIN" : elevation > 0.68 ? "HIGHLAND" : moisture > 0.58 ? "TEMPERATE_FOREST" : "GRASSLAND";
        return new TerrainCell(elevationValue, moistureValue, biome);
    }

    private double deterministicVariation(int x, int y, long seed) {
        long value = seed ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) y * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L; value ^= value >>> 27; value *= 0x94D049BB133111EBL; value ^= value >>> 31;
        return ((value & 0xFFFF) / 65535.0) - 0.5;
    }

    private List<PreviewMarker> markersFor(GenesisRequest request) {
        List<MarkerSpec> specifications = List.of(
                new MarkerSpec("RESOURCE", "Freshwater spring", "GRASSLAND", "TEMPERATE_FOREST"), new MarkerSpec("RESOURCE", "Freshwater spring", "HIGHLAND", "WETLAND"), new MarkerSpec("RESOURCE", "Old-growth timber", "TEMPERATE_FOREST"), new MarkerSpec("RESOURCE", "Old-growth timber", "TEMPERATE_FOREST"), new MarkerSpec("RESOURCE", "Wild herb grove", "TEMPERATE_FOREST", "GRASSLAND"), new MarkerSpec("RESOURCE", "Wild herb grove", "WETLAND", "TEMPERATE_FOREST"), new MarkerSpec("RESOURCE", "Edible-root patch", "GRASSLAND", "TEMPERATE_FOREST"), new MarkerSpec("RESOURCE", "Edible-root patch", "GRASSLAND", "WETLAND"), new MarkerSpec("RESOURCE", "Reed marsh", "WETLAND"), new MarkerSpec("RESOURCE", "Reed marsh", "WETLAND"), new MarkerSpec("RESOURCE", "Clay beds", "WETLAND", "GRASSLAND"), new MarkerSpec("RESOURCE", "Flint field", "HIGHLAND", "GRASSLAND"), new MarkerSpec("RESOURCE", "Stone outcrop", "HIGHLAND", "MOUNTAIN"), new MarkerSpec("RESOURCE", "Stone outcrop", "HIGHLAND", "MOUNTAIN"), new MarkerSpec("RESOURCE", "Iron vein", "MOUNTAIN", "HIGHLAND"), new MarkerSpec("RESOURCE", "Copper seam", "MOUNTAIN", "HIGHLAND"), new MarkerSpec("RESOURCE", "Salt marsh", "WETLAND", "OCEAN"), new MarkerSpec("RESOURCE", "Mushroom hollow", "TEMPERATE_FOREST"), new MarkerSpec("RESOURCE", "Fiber grassland", "GRASSLAND"), new MarkerSpec("RESOURCE", "Shelter grove", "TEMPERATE_FOREST"),
                new MarkerSpec("WILDLIFE", "Deer range", "TEMPERATE_FOREST", "GRASSLAND"), new MarkerSpec("WILDLIFE", "Deer range", "TEMPERATE_FOREST", "GRASSLAND"), new MarkerSpec("WILDLIFE", "Boar range", "WETLAND", "TEMPERATE_FOREST"), new MarkerSpec("WILDLIFE", "Elk range", "GRASSLAND", "HIGHLAND"), new MarkerSpec("WILDLIFE", "Wolf pack ground", "TEMPERATE_FOREST", "HIGHLAND"), new MarkerSpec("WILDLIFE", "Bear den", "TEMPERATE_FOREST", "MOUNTAIN"), new MarkerSpec("WILDLIFE", "Marsh-fowl nesting", "WETLAND"), new MarkerSpec("WILDLIFE", "Hare warren", "GRASSLAND"), new MarkerSpec("WILDLIFE", "Fox earth", "TEMPERATE_FOREST", "GRASSLAND"), new MarkerSpec("WILDLIFE", "Goat cliff range", "HIGHLAND", "MOUNTAIN"), new MarkerSpec("WILDLIFE", "Beaver lodge", "WETLAND"), new MarkerSpec("WILDLIFE", "Otter waterway", "WETLAND", "OCEAN"),
                new MarkerSpec("MONSTER", "Bog warden lair", "WETLAND"), new MarkerSpec("MONSTER", "Ridge stalker lair", "HIGHLAND", "MOUNTAIN"), new MarkerSpec("MONSTER", "Glasswing roost", "MOUNTAIN", "HIGHLAND"), new MarkerSpec("MONSTER", "Mire hydra nest", "WETLAND"), new MarkerSpec("MONSTER", "Deepwater maw", "OCEAN"), new MarkerSpec("MONSTER", "Dusk prowler territory", "TEMPERATE_FOREST"), new MarkerSpec("MONSTER", "Thornback wallow", "GRASSLAND", "TEMPERATE_FOREST"), new MarkerSpec("MONSTER", "Ash hound den", "HIGHLAND", "MOUNTAIN"), new MarkerSpec("MONSTER", "Fen siren pool", "WETLAND"), new MarkerSpec("MONSTER", "Gloom moth colony", "TEMPERATE_FOREST"),
                new MarkerSpec("RUIN", "Ancient observatory", "HIGHLAND", "MOUNTAIN"), new MarkerSpec("RUIN", "Sunken shrine", "WETLAND"), new MarkerSpec("RUIN", "Collapsed causeway", "GRASSLAND", "WETLAND"), new MarkerSpec("RUIN", "Overgrown watchtower", "TEMPERATE_FOREST"), new MarkerSpec("RUIN", "Flooded archive", "WETLAND"), new MarkerSpec("RUIN", "Buried granary", "GRASSLAND"), new MarkerSpec("RUIN", "Broken aqueduct", "HIGHLAND", "GRASSLAND"));
        List<PreviewMarker> markers = new ArrayList<>();
        for (int index = 0; index < specifications.size(); index++) {
            MarkerSpec spec = specifications.get(index);
            markers.add(marker(spec.category(), spec.label(), spec.biomes(), request, 11 + index * 17));
        }
        return List.copyOf(markers);
    }

    /** Shared deterministic plan used by both the creator preview and canonical ecology seeding. */
    public List<PreviewMarker> markerPlan(GenesisRequest request) {
        validateDimensions(request);
        return markersFor(request);
    }

    private PreviewMarker marker(String category, String label, String[] accepted, GenesisRequest request, int salt) {
        int count = request.widthChunks() * request.heightChunks(), start = Math.floorMod((int) (request.seed() ^ (salt * 0x9E3779B9L)), count);
        for (int attempt = 0; attempt < count; attempt++) {
            int index = Math.floorMod(start + attempt * 37, count), x = index % request.widthChunks(), y = index / request.widthChunks();
            String biome = terrainAt(x, y, request.widthChunks(), request.heightChunks(), request.seed(), true).biome();
            for (String permitted : accepted) if (permitted.equals(biome)) return new PreviewMarker(category, label, x, y);
        }
        return new PreviewMarker(category, label, request.widthChunks() / 2, request.heightChunks() / 2);
    }

    private void drawMarker(Graphics2D g, PreviewMarker marker, int imageWidth, int mapHeight, int header, GenesisRequest request) {
        double x = ((marker.x() + .5) / request.widthChunks()) * imageWidth, y = header + ((marker.y() + .5) / request.heightChunks()) * mapHeight;
        Color color = switch (marker.category()) { case "RESOURCE" -> new Color(236, 190, 92); case "WILDLIFE" -> new Color(126, 214, 185); case "MONSTER" -> new Color(224, 101, 104); default -> new Color(191, 148, 225); };
        g.setColor(new Color(15, 20, 19, 190)); g.fillOval((int) x - 12, (int) y - 12, 24, 24); g.setColor(color);
        if (marker.category().equals("WILDLIFE")) g.fillOval((int) x - 5, (int) y - 5, 10, 10);
        else if (marker.category().equals("MONSTER")) { Path2D triangle = new Path2D.Double(); triangle.moveTo(x, y - 7); triangle.lineTo(x + 7, y + 6); triangle.lineTo(x - 7, y + 6); triangle.closePath(); g.fill(triangle); }
        else g.fillRect((int) x - 5, (int) y - 5, 10, 10);
        g.setColor(new Color(250, 248, 239)); g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13)); g.drawString(marker.label(), (int) x + 15, (int) y + 5);
    }

    private void drawLegend(Graphics2D g, int imageWidth) {
        String[] labels = {"■ Resource", "● Wildlife range", "▲ Monster lair", "■ Ancient ruin"}; Color[] colors = {new Color(236, 190, 92), new Color(126, 214, 185), new Color(224, 101, 104), new Color(191, 148, 225)};
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13)); for (int i = 0; i < labels.length; i++) { g.setColor(colors[i]); g.drawString(labels[i], imageWidth - 475 + i * 116, 67); }
    }

    private Color shade(Color color, int elevation) { float factor = .78f + elevation / 1000f * .28f; return new Color(Math.min(255, (int) (color.getRed() * factor)), Math.min(255, (int) (color.getGreen() * factor)), Math.min(255, (int) (color.getBlue() * factor))); }
    private Color colorFor(String biome) { return switch (biome) { case "OCEAN" -> new Color(42, 87, 123); case "WETLAND" -> new Color(73, 111, 104); case "TEMPERATE_FOREST" -> new Color(54, 103, 61); case "GRASSLAND" -> new Color(144, 145, 83); case "HIGHLAND" -> new Color(120, 111, 82); case "MOUNTAIN" -> new Color(122, 123, 121); default -> Color.MAGENTA; }; }
    private Path previewPath() { return exportDirectory.resolve("overseer-map.png"); }

    public record GenesisRequest(long seed, int widthChunks, int heightChunks) { public static GenesisRequest mvpDefault() { return new GenesisRequest(681_013_497L, 28, 20); } }
    public record PreviewSummary(long seed, int widthChunks, int heightChunks, Map<String, Integer> biomeCounts, List<PreviewMarker> markers, String previewPath) { }
    public record GenesisSummary(UUID worldId, long seed, int widthChunks, int heightChunks, Map<String, Integer> biomeCounts, String previewPath, Instant generatedAt) { }
    private record TerrainCell(int elevation, int moisture, String biome) { }
    public record PreviewMarker(String category, String label, int x, int y) { }
    private record MarkerSpec(String category, String label, String... biomes) { }
}
