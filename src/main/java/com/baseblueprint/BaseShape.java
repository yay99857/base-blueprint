package com.baseblueprint;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The footprint of a base on a width × length grid, using the same rule as pixel circle generators:
 * a cell belongs to a circle when its centre lies inside the ellipse that touches the grid edges.
 * The edge (used for outline floors and walls) is every filled cell with an empty neighbour, diagonals
 * included, so the ring is always closed with no diagonal gaps.
 */
public final class BaseShape {
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
                if (shape == BlueprintSettings.Shape.SQUARE) {
                    filled[z * width + x] = true;
                } else {
                    double dx = (x + 0.5 - rx) / rx;
                    double dz = (z + 0.5 - rz) / rz;
                    filled[z * width + x] = dx * dx + dz * dz <= 1.0;
                }
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

    /** Number of blocks the blueprint needs on open ground. */
    public int blockCount(BlueprintSettings s) {
        int floorCells = 0, edgeCells = 0;
        for (int i = 0; i < filled.length; i++) {
            if (filled[i]) floorCells++;
            if (edge[i]) edgeCells++;
        }
        int floor = switch (s.floor()) {
            case FILLED -> floorCells;
            case OUTLINE -> edgeCells;
            case NONE -> 0;
        };
        return floor + edgeCells * s.wallHeight();
    }

    /** Every position to build, floor first and then the walls from the bottom up. */
    public List<BlockPos> positions(BlueprintSettings s, BlockPos origin) {
        int x0 = startX(s, origin), z0 = startZ(s, origin), y = origin.getY();
        List<BlockPos> out = new ArrayList<>();
        if (s.floor() != BlueprintSettings.Floor.NONE) {
            boolean outlineOnly = s.floor() == BlueprintSettings.Floor.OUTLINE;
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    if (outlineOnly ? edge(x, z) : filled(x, z)) out.add(new BlockPos(x0 + x, y, z0 + z));
                }
            }
        }
        int base = wallStart(s);
        for (int h = 0; h < s.wallHeight(); h++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    if (edge(x, z)) out.add(new BlockPos(x0 + x, y + base + h, z0 + z));
                }
            }
        }
        return out;
    }
}
