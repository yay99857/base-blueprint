package com.baseblueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

/** What the blueprint builds. Stored on the item as a data component. */
public record BlueprintSettings(Shape shape, int width, int length, Floor floor, int wallHeight) {
    public static final BlueprintSettings DEFAULT = new BlueprintSettings(Shape.CIRCLE, 15, 15, Floor.FILLED, 0);

    public enum Shape implements StringRepresentable {
        CIRCLE("circle"), SQUARE("square");

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

    public static final Codec<BlueprintSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Shape.CODEC.fieldOf("shape").forGetter(BlueprintSettings::shape),
            Codec.intRange(1, 256).fieldOf("width").forGetter(BlueprintSettings::width),
            Codec.intRange(1, 256).fieldOf("length").forGetter(BlueprintSettings::length),
            Floor.CODEC.fieldOf("floor").forGetter(BlueprintSettings::floor),
            Codec.intRange(0, 64).fieldOf("wall_height").forGetter(BlueprintSettings::wallHeight)
    ).apply(i, BlueprintSettings::new));

    public static final StreamCodec<ByteBuf, BlueprintSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(n -> Shape.values()[Mth.clamp(n, 0, Shape.values().length - 1)], Shape::ordinal),
            BlueprintSettings::shape,
            ByteBufCodecs.VAR_INT, BlueprintSettings::width,
            ByteBufCodecs.VAR_INT, BlueprintSettings::length,
            ByteBufCodecs.VAR_INT.map(n -> Floor.values()[Mth.clamp(n, 0, Floor.values().length - 1)], Floor::ordinal),
            BlueprintSettings::floor,
            ByteBufCodecs.VAR_INT, BlueprintSettings::wallHeight,
            BlueprintSettings::new);

    /** Clamps every value to the server limits, so a modified client cannot ask for a 10 000-block wall. */
    public BlueprintSettings clamped() {
        int max = BlueprintConfig.maxSize();
        return new BlueprintSettings(shape, Mth.clamp(width, 1, max), Mth.clamp(length, 1, max), floor,
                Mth.clamp(wallHeight, 0, BlueprintConfig.maxWallHeight()));
    }

    public BlueprintSettings withShape(Shape s) {
        return new BlueprintSettings(s, width, length, floor, wallHeight);
    }

    public BlueprintSettings withWidth(int w) {
        return new BlueprintSettings(shape, w, length, floor, wallHeight);
    }

    public BlueprintSettings withLength(int l) {
        return new BlueprintSettings(shape, width, l, floor, wallHeight);
    }

    public BlueprintSettings withFloor(Floor f) {
        return new BlueprintSettings(shape, width, length, f, wallHeight);
    }

    public BlueprintSettings withWallHeight(int h) {
        return new BlueprintSettings(shape, width, length, floor, h);
    }
}
