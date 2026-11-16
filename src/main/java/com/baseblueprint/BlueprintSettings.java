package com.baseblueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

/**
 * What the blueprint builds. Stored on the item as a data component; fields added after 1.0 are optional in the
 * codec, so blueprints made with 1.0 keep working.
 *
 * @param door       leave a 1×2 opening in the wall on the side facing the player
 * @param roof       nothing, a flat roof, or a dome (round shapes only) on top of the walls
 * @param foundation fill the gap under the floor down to the ground, up to {@link BlueprintConfig#maxFoundation()}
 * @param pick       what sneak + right-click on a block sets: the floor material or the wall (and roof) material
 */
public record BlueprintSettings(Shape shape, int width, int length, Floor floor, int wallHeight,
                                boolean door, Roof roof, boolean foundation, Pick pick) {
    public static final BlueprintSettings DEFAULT = new BlueprintSettings(Shape.CIRCLE, 15, 15, Floor.FILLED, 0);

    /** The 1.0 settings: no door, no roof, no foundation. */
    public BlueprintSettings(Shape shape, int width, int length, Floor floor, int wallHeight) {
        this(shape, width, length, floor, wallHeight, false, Roof.NONE, false, Pick.FLOOR);
    }

    public enum Shape implements StringRepresentable {
        CIRCLE("circle"), SQUARE("square"), OCTAGON("octagon"), HEXAGON("hexagon");

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);
        private final String name;

        Shape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Floor implements StringRepresentable {
        FILLED("filled"), OUTLINE("outline"), NONE("none");

        public static final Codec<Floor> CODEC = StringRepresentable.fromEnum(Floor::values);
        private final String name;

        Floor(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Roof implements StringRepresentable {
        NONE("none"), FLAT("flat"), DOME("dome");

        public static final Codec<Roof> CODEC = StringRepresentable.fromEnum(Roof::values);
        private final String name;

        Roof(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Pick implements StringRepresentable {
        FLOOR("floor"), WALLS("walls");

        public static final Codec<Pick> CODEC = StringRepresentable.fromEnum(Pick::values);
        private final String name;

        Pick(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final Codec<BlueprintSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Shape.CODEC.fieldOf("shape").forGetter(BlueprintSettings::shape),
            Codec.intRange(1, 256).fieldOf("width").forGetter(BlueprintSettings::width),
            Codec.intRange(1, 256).fieldOf("length").forGetter(BlueprintSettings::length),
            Floor.CODEC.fieldOf("floor").forGetter(BlueprintSettings::floor),
            Codec.intRange(0, 64).fieldOf("wall_height").forGetter(BlueprintSettings::wallHeight),
            Codec.BOOL.optionalFieldOf("door", false).forGetter(BlueprintSettings::door),
            Roof.CODEC.optionalFieldOf("roof", Roof.NONE).forGetter(BlueprintSettings::roof),
            Codec.BOOL.optionalFieldOf("foundation", false).forGetter(BlueprintSettings::foundation),
            Pick.CODEC.optionalFieldOf("pick", Pick.FLOOR).forGetter(BlueprintSettings::pick)
    ).apply(i, BlueprintSettings::new));

    private static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(E[] values) {
        return ByteBufCodecs.VAR_INT.map(n -> values[Mth.clamp(n, 0, values.length - 1)], Enum::ordinal);
    }

    /** The options added in 1.1, grouped so the stream codec stays within composite's arity. */
    private record Extras(boolean door, Roof roof, boolean foundation, Pick pick) {
        static final StreamCodec<ByteBuf, Extras> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Extras::door,
                enumCodec(Roof.values()), Extras::roof,
                ByteBufCodecs.BOOL, Extras::foundation,
                enumCodec(Pick.values()), Extras::pick,
                Extras::new);
    }

    public static final StreamCodec<ByteBuf, BlueprintSettings> STREAM_CODEC = StreamCodec.composite(
            enumCodec(Shape.values()), BlueprintSettings::shape,
            ByteBufCodecs.VAR_INT, BlueprintSettings::width,
            ByteBufCodecs.VAR_INT, BlueprintSettings::length,
            enumCodec(Floor.values()), BlueprintSettings::floor,
            ByteBufCodecs.VAR_INT, BlueprintSettings::wallHeight,
            Extras.CODEC, s -> new Extras(s.door, s.roof, s.foundation, s.pick),
            (shape, width, length, floor, wallHeight, extras) -> new BlueprintSettings(shape, width, length, floor,
                    wallHeight, extras.door(), extras.roof(), extras.foundation(), extras.pick()));

    /** Clamps every value to the server limits, so a modified client cannot ask for a 10 000-block wall. */
    public BlueprintSettings clamped() {
        int max = BlueprintConfig.maxSize();
        return new BlueprintSettings(shape, Mth.clamp(width, 1, max), Mth.clamp(length, 1, max), floor,
                Mth.clamp(wallHeight, 0, BlueprintConfig.maxWallHeight()), door, roof, foundation, pick);
    }

    /** The roof actually built: none without walls, and a dome only on a circle (other shapes get a flat roof). */
    public Roof effectiveRoof() {
        if (wallHeight <= 0 || roof == Roof.NONE) return Roof.NONE;
        return roof == Roof.DOME && shape != Shape.CIRCLE ? Roof.FLAT : roof;
    }

    public BlueprintSettings withShape(Shape s) {
        return new BlueprintSettings(s, width, length, floor, wallHeight, door, roof, foundation, pick);
    }

    public BlueprintSettings withWidth(int w) {
        return new BlueprintSettings(shape, w, length, floor, wallHeight, door, roof, foundation, pick);
    }

    public BlueprintSettings withLength(int l) {
        return new BlueprintSettings(shape, width, l, floor, wallHeight, door, roof, foundation, pick);
    }

    public BlueprintSettings withFloor(Floor f) {
        return new BlueprintSettings(shape, width, length, f, wallHeight, door, roof, foundation, pick);
    }

    public BlueprintSettings withWallHeight(int h) {
        return new BlueprintSettings(shape, width, length, floor, h, door, roof, foundation, pick);
    }

    public BlueprintSettings withDoor(boolean d) {
        return new BlueprintSettings(shape, width, length, floor, wallHeight, d, roof, foundation, pick);
    }

    public BlueprintSettings withRoof(Roof r) {
        return new BlueprintSettings(shape, width, length, floor, wallHeight, door, r, foundation, pick);
    }

    public BlueprintSettings withFoundation(boolean f) {
        return new BlueprintSettings(shape, width, length, floor, wallHeight, door, roof, f, pick);
    }

    public BlueprintSettings withPick(Pick p) {
        return new BlueprintSettings(shape, width, length, floor, wallHeight, door, roof, foundation, p);
    }
}
