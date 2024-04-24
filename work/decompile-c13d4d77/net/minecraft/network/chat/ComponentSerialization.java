package net.minecraft.network.chat;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.INamable;

public class ComponentSerialization {

    public static final Codec<IChatBaseComponent> CODEC = ExtraCodecs.recursive("Component", ComponentSerialization::createCodec);
    public static final Codec<IChatBaseComponent> FLAT_CODEC = ExtraCodecs.FLAT_JSON.flatXmap((jsonelement) -> {
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, jsonelement);
    }, (ichatbasecomponent) -> {
        return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, ichatbasecomponent);
    });

    public ComponentSerialization() {}

    private static IChatMutableComponent createFromList(List<IChatBaseComponent> list) {
        IChatMutableComponent ichatmutablecomponent = ((IChatBaseComponent) list.get(0)).copy();

        for (int i = 1; i < list.size(); ++i) {
            ichatmutablecomponent.append((IChatBaseComponent) list.get(i));
        }

        return ichatmutablecomponent;
    }

    public static <T extends INamable, E> MapCodec<E> createLegacyComponentMatcher(T[] at, Function<T, MapCodec<? extends E>> function, Function<E, T> function1, String s) {
        MapCodec<E> mapcodec = new ComponentSerialization.a<>(Stream.of(at).map(function).toList(), (object) -> {
            return (MapEncoder) function.apply((INamable) function1.apply(object));
        });
        Codec<T> codec = INamable.fromValues(() -> {
            return at;
        });
        MapCodec<E> mapcodec1 = codec.dispatchMap(s, function1, (inamable) -> {
            return ((MapCodec) function.apply(inamable)).codec();
        });
        MapCodec<E> mapcodec2 = new ComponentSerialization.b<>(s, mapcodec1, mapcodec);

        return ExtraCodecs.orCompressed((MapCodec) mapcodec2, mapcodec1);
    }

    private static Codec<IChatBaseComponent> createCodec(Codec<IChatBaseComponent> codec) {
        ComponentContents.a<?>[] acomponentcontents_a = new ComponentContents.a[]{LiteralContents.TYPE, TranslatableContents.TYPE, KeybindContents.TYPE, ScoreContents.TYPE, SelectorContents.TYPE, NbtContents.TYPE};
        MapCodec<ComponentContents> mapcodec = createLegacyComponentMatcher(acomponentcontents_a, ComponentContents.a::codec, ComponentContents::type, "type");
        Codec<IChatBaseComponent> codec1 = RecordCodecBuilder.create((instance) -> {
            return instance.group(mapcodec.forGetter(IChatBaseComponent::getContents), ExtraCodecs.strictOptionalField(ExtraCodecs.nonEmptyList(codec.listOf()), "extra", List.of()).forGetter(IChatBaseComponent::getSiblings), ChatModifier.ChatModifierSerializer.MAP_CODEC.forGetter(IChatBaseComponent::getStyle)).apply(instance, IChatMutableComponent::new);
        });

        return Codec.either(Codec.either(Codec.STRING, ExtraCodecs.nonEmptyList(codec.listOf())), codec1).xmap((either) -> {
            return (IChatBaseComponent) either.map((either1) -> {
                return (IChatBaseComponent) either1.map(IChatBaseComponent::literal, ComponentSerialization::createFromList);
            }, (ichatbasecomponent) -> {
                return ichatbasecomponent;
            });
        }, (ichatbasecomponent) -> {
            String s = ichatbasecomponent.tryCollapseToString();

            return s != null ? Either.left(Either.left(s)) : Either.right(ichatbasecomponent);
        });
    }

    private static class a<T> extends MapCodec<T> {

        private final List<MapCodec<? extends T>> codecs;
        private final Function<T, MapEncoder<? extends T>> encoderGetter;

        public a(List<MapCodec<? extends T>> list, Function<T, MapEncoder<? extends T>> function) {
            this.codecs = list;
            this.encoderGetter = function;
        }

        public <S> DataResult<T> decode(DynamicOps<S> dynamicops, MapLike<S> maplike) {
            Iterator iterator = this.codecs.iterator();

            DataResult dataresult;

            do {
                if (!iterator.hasNext()) {
                    return DataResult.error(() -> {
                        return "No matching codec found";
                    });
                }

                MapDecoder<? extends T> mapdecoder = (MapDecoder) iterator.next();

                dataresult = mapdecoder.decode(dynamicops, maplike);
            } while (!dataresult.result().isPresent());

            return dataresult;
        }

        public <S> RecordBuilder<S> encode(T t0, DynamicOps<S> dynamicops, RecordBuilder<S> recordbuilder) {
            MapEncoder<T> mapencoder = (MapEncoder) this.encoderGetter.apply(t0);

            return mapencoder.encode(t0, dynamicops, recordbuilder);
        }

        public <S> Stream<S> keys(DynamicOps<S> dynamicops) {
            return this.codecs.stream().flatMap((mapcodec) -> {
                return mapcodec.keys(dynamicops);
            }).distinct();
        }

        public String toString() {
            return "FuzzyCodec[" + this.codecs + "]";
        }
    }

    private static class b<T> extends MapCodec<T> {

        private final String typeFieldName;
        private final MapCodec<T> typed;
        private final MapCodec<T> fuzzy;

        public b(String s, MapCodec<T> mapcodec, MapCodec<T> mapcodec1) {
            this.typeFieldName = s;
            this.typed = mapcodec;
            this.fuzzy = mapcodec1;
        }

        public <O> DataResult<T> decode(DynamicOps<O> dynamicops, MapLike<O> maplike) {
            return maplike.get(this.typeFieldName) != null ? this.typed.decode(dynamicops, maplike) : this.fuzzy.decode(dynamicops, maplike);
        }

        public <O> RecordBuilder<O> encode(T t0, DynamicOps<O> dynamicops, RecordBuilder<O> recordbuilder) {
            return this.fuzzy.encode(t0, dynamicops, recordbuilder);
        }

        public <T1> Stream<T1> keys(DynamicOps<T1> dynamicops) {
            return Stream.concat(this.typed.keys(dynamicops), this.fuzzy.keys(dynamicops)).distinct();
        }
    }
}
