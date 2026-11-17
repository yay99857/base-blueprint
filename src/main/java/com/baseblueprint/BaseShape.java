package com.baseblueprint;

import net.minecraft.core.BlockPos;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The footprint of a base on a width × length grid. A cell belongs to the shape when its centre lies inside it,
 * the same rule as pixel circle generators: the circle is the ellipse touching the grid edges, the octagon a
 * regular octagon (corners cut at 45°) and the hexagon a pointed hexagon (points on the left and right).
 * The edge (used for outline floors and walls) is every filled cell with an empty neighbour, diagonals
 * included, so the ring is always closed with no diagonal gaps.
 */
public final class BaseShape {
    private static final double OCTAGON_LIMIT = 2 - 2 / (2 + Math.sqrt(2));

    /** What a position is for: it decides the material (floor and foundation, or walls and roof). */
    public enum Part {
        FLOOR, FOUNDATION, WALL, ROOF;

        public boolean usesWallMaterial() {
            return this == WALL || this == ROOF;
        }
    }

    public record Placement(BlockPos pos, Part part) {
    }

    private final int width;
    private final int length;
    private final boolean[] filled;
    private final boolean[] edge;

    private BaseShape(int width, int length, boolean[] filled, boolean[] edge) {
        this.width = width;
        this.length = length;
        this.filled = filled;
        this.edge = edge;
    }

    public static BaseShape of(BlueprintSettings.Shape shape, int width, int length) {
        boolean[] filled = new boolean[width * length];
        double rx = width / 2.0;
        double rz = length / 2.0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                double u = Math.abs((x + 0.5 - rx) / rx);
                double v = Math.abs((z + 0.5 - rz) / rz);
                filled[z * width + x] = switch (shape) {
                    case SQUARE -> true;
                    case CIRCLE -> u * u + v * v <= 1.0;
                    // regular octagon in the box: each corner cut along a 45° line, |u| + |v| <= 2 - 2/(2+√2)
                    case OCTAGON -> u + v <= OCTAGON_LIMIT + 1e-9;
                    // points at u = ±1, flat top and bottom: the slanted sides run from (1, 0) to (0.5, 1)
                    case HEXAGON -> u <= 1 - 0.5 * v + 1e-9;
                };
            }
        }
        boolean[] edge = new boolean[width * length];
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                if (!filled[z * width + x]) continue;
                outer:
                for (int oz = -1; oz <= 1; oz++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        int nx = x + ox, nz = z + oz;
                        if (nx < 0 || nz < 0 || nx >= width || nz >= length || !filled[nz * width + nx]) {
                            edge[z * width + x] = true;
                            break outer;
                        }
                    }
                }
            }
        }
        return new BaseShape(width, length, filled, edge);
    }

    public static BaseShape of(BlueprintSettings s) {
        return of(s.shape(), s.width(), s.length());
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public boolean filled(int x, int z) {
        return filled[z * width + x];
    }

    public boolean edge(int x, int z) {
        return edge[z * width + x];
    }

    /** World X/Z of grid cell 0: the base is centred on the origin (odd sizes exactly, even sizes leaning -X/-Z). */
    public static int startX(BlueprintSettings s, BlockPos origin) {
        return origin.getX() - s.width() / 2;
    }

    public static int startZ(BlueprintSettings s, BlockPos origin) {
        return origin.getZ() - s.length() / 2;
    }

    /** Layer above the floor where walls begin (walls sit on the ground when there is no floor). */
    public static int wallStart(BlueprintSettings s) {
        return s.floor() == BlueprintSettings.Floor.NONE ? 0 : 1;
    }

    /** Number of blocks the blueprint needs on open ground (no foundation; door on the south side). */
    public int blockCount(BlueprintSettings s) {
        return positions(s, BlockPos.ZERO, Direction.SOUTH).size();
    }

    /** Every position to build: floor, then walls from the bottom up, then the roof. */
    public List<BlockPos> positions(BlueprintSettings s, BlockPos origin) {
        List<BlockPos> out = new ArrayList<>();
        for (Placement p : placements(s, origin, Direction.SOUTH)) out.add(p.pos());
        return out;
    }

    public List<BlockPos> positions(BlueprintSettings s, BlockPos origin, @Nullable Direction doorSide) {
        List<BlockPos> out = new ArrayList<>();
        for (Placement p : placements(s, origin, doorSide)) out.add(p.pos());
        return out;
    }

    /**
     * Every position to build and what it is for: floor, then walls from the bottom up, then the roof.
     * With {@code s.door()} the walls get a 1×2 opening in the middle of the {@code doorSide} side.
     */
    public List<Placement> placements(BlueprintSettings s, BlockPos origin, @Nullable Direction doorSide) {
        int x0 = startX(s, origin), z0 = startZ(s, origin), y = origin.getY();
        List<Placement> out = new ArrayList<>();
        if (s.floor() != BlueprintSettings.Floor.NONE) {
            boolean outlineOnly = s.floor() == BlueprintSettings.Floor.OUTLINE;
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    if (outlineOnly ? edge(x, z) : filled(x, z)) out.add(new Placement(new BlockPos(x0 + x, y, z0 + z), Part.FLOOR));
                }
            }
        }
        int base = wallStart(s);
        boolean[] doorway = s.door() && doorSide != null && s.wallHeight() > 0 ? doorway(doorSide) : null;
        int storeys = s.effectiveStoreys();
        int hatch = storeys > 1 ? hatch(doorSide == null ? Direction.SOUTH : doorSide) : -1;
        int wallY = y + base;
        for (int k = 0; k < storeys; k++) {
            if (k > 0) {
                // the floor between storeys: the wall goes on through its edge, the rest is floor with a hatch
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        if (!filled(x, z) || z * width + x == hatch) continue;
                        out.add(new Placement(new BlockPos(x0 + x, wallY, z0 + z), edge(x, z) ? Part.WALL : Part.FLOOR));
                    }
                }
                wallY++;
            }
            for (int h = 0; h < s.wallHeight(); h++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        if (!edge(x, z)) continue;
                        if (k == 0 && doorway != null && h < 2 && doorway[z * width + x]) continue;
                        out.add(new Placement(new BlockPos(x0 + x, wallY + h, z0 + z), Part.WALL));
                    }
                }
            }
            wallY += s.wallHeight();
        }
        int roofY = wallY;
        switch (s.effectiveRoof()) {
            case FLAT -> {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        if (filled(x, z)) out.add(new Placement(new BlockPos(x0 + x, roofY, z0 + z), Part.ROOF));
                    }
                }
            }
            case DOME -> {
                for (int[] c : domeShell()) {
                    out.add(new Placement(new BlockPos(x0 + c[0], roofY + c[1], z0 + c[2]), Part.ROOF));
                }
            }
            case NONE -> {
            }
        }
        return out;
    }

    /**
     * The 1×1 opening left in each upper floor for a ladder: the first inside cell next to the wall opposite the
     * door, so the ladder goes up that wall. -1 when the footprint has no inside.
     */
    private int hatch(Direction doorSide) {
        int x, z, dx, dz;
        switch (doorSide.getOpposite()) {
            case EAST -> { x = width - 1; z = length / 2; dx = -1; dz = 0; }
            case WEST -> { x = 0; z = length / 2; dx = 1; dz = 0; }
            case NORTH -> { x = width / 2; z = 0; dx = 0; dz = 1; }
            default -> { x = width / 2; z = length - 1; dx = 0; dz = -1; }
        }
        while (x >= 0 && z >= 0 && x < width && z < length) {
            if (filled(x, z) && !edge(x, z)) return z * width + x;
            x += dx;
            z += dz;
        }
        return -1;
    }

    /** Height of the dome in layers: half the smaller side, so a circle gets a half sphere. */
    public int domeHeight() {
        return Math.max(1, (int) Math.round(Math.min(width, length) / 2.0));
    }

    /**
     * Cells (x, layer, z) of a half-ellipsoid shell over the footprint: inside the ellipsoid and touching the
     * outside on some face. Layers below 0 count as inside, so the dome has no bottom disc.
     */
    private List<int[]> domeShell() {
        int height = domeHeight();
        double rx = width / 2.0, rz = length / 2.0;
        List<int[]> shell = new ArrayList<>();
        for (int h = 0; h < height; h++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    if (!insideDome(x, h, z, rx, height, rz)) continue;
                    boolean surface = !insideDome(x + 1, h, z, rx, height, rz) || !insideDome(x - 1, h, z, rx, height, rz)
                            || !insideDome(x, h, z + 1, rx, height, rz) || !insideDome(x, h, z - 1, rx, height, rz)
                            || !insideDome(x, h + 1, z, rx, height, rz);
                    if (surface) shell.add(new int[]{x, h, z});
                }
            }
        }
        return shell;
    }

    private boolean insideDome(int x, int h, int z, double rx, int height, double rz) {
        if (x < 0 || z < 0 || x >= width || z >= length || !filled(x, z)) return false;
        if (h < 0) return true;
        double u = (x + 0.5 - rx) / rx, v = (z + 0.5 - rz) / rz, w = (h + 0.5) / height;
        return u * u + v * v + w * w <= 1.0;
    }

    /**
     * Wall cells to leave open for the door: from the middle of the given side inwards, every edge cell in a row
     * (a slanted or pointed side can make the wall two cells thick there).
     */
    private boolean[] doorway(Direction side) {
        boolean[] open = new boolean[width * length];
        int x, z, dx, dz;
        switch (side) {
            case EAST -> { x = width - 1; z = length / 2; dx = -1; dz = 0; }
            case WEST -> { x = 0; z = length / 2; dx = 1; dz = 0; }
            case NORTH -> { x = width / 2; z = 0; dx = 0; dz = 1; }
            default -> { x = width / 2; z = length - 1; dx = 0; dz = -1; }
        }
        // the outermost filled cell on that line, then inwards while it is still wall
        while (x >= 0 && z >= 0 && x < width && z < length && !filled(x, z)) {
            x += dx;
            z += dz;
        }
        for (int i = 0; i < 3 && x >= 0 && z >= 0 && x < width && z < length && edge(x, z); i++) {
            open[z * width + x] = true;
            x += dx;
            z += dz;
        }
        return open;
    }

    /** The side of the base that faces a point (the player): the door goes there. */
    public static Direction sideFacing(BlueprintSettings s, BlockPos origin, double px, double pz) {
        double cx = startX(s, origin) + s.width() / 2.0, cz = startZ(s, origin) + s.length() / 2.0;
        double dx = (px - cx) / Math.max(1, s.width()), dz = (pz - cz) / Math.max(1, s.length());
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
