package com.devosphere.draugr.world;

/**
 * Which way is which, in one place (#224).
 *
 * <p>The world's grid runs with <b>y increasing southward</b>: north is {@code grid_y - 1}. That is not a free
 * choice — six queries across the codebase derive a south-facing slope as "the chunk at {@code grid_y - 1} is
 * higher", and the whole warmth model rests on it. Writing a second convention anywhere would make a place look
 * like one thing and feel like another.
 *
 * <p>So the convention lives here and the two readers of it agree by test rather than by luck:
 * {@code ChronicleActionService.Direction}, which parses "go north" into a move, and
 * {@link VisualContextService}, which says which way the next ground lies.
 */
public final class Compass {

    private Compass() { }

    /** North is y-1, south is y+1, east is x+1, west is x-1. The eight-point ring, in order. */
    public static final String[] RING = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};

    /**
     * The bearing from here to a place {@code dx} east and {@code dy} south, or {@code null} for no distance at
     * all. Returns a compound where both axes differ — "northeast", never "north and east".
     */
    public static String of(int dx, int dy) {
        String ns = dy < 0 ? "north" : dy > 0 ? "south" : "";
        String ew = dx > 0 ? "east" : dx < 0 ? "west" : "";
        String both = ns + ew;
        return both.isEmpty() ? null : both;
    }

    /** The next bearing round the ring, or the same one back if it is not a bearing this knows. */
    public static String rotate(String bearing, boolean clockwise) {
        for (int i = 0; i < RING.length; i++)
            if (RING[i].equals(bearing)) return RING[(i + (clockwise ? 1 : RING.length - 1)) % RING.length];
        return bearing;
    }
}
