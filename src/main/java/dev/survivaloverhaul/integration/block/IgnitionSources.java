package dev.survivaloverhaul.integration.block;

import dev.survivaloverhaul.SurvivalOverhaul;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What counts as an open flame a torch can be lit from.
 *
 * <p>Membership is a block tag rather than a list in code, so a data pack can add
 * a modded brazier without touching the mod. The burning test cannot live in the
 * tag though: a campfire, a candle and this mod's own torch are all in it, and
 * all three can be sitting there cold.
 */
public final class IgnitionSources {

    /** {@code data/survivaloverhaul/tags/block/lights_torches.json}. */
    public static final TagKey<Block> LIGHTS_TORCHES =
            TagKey.create(Registries.BLOCK, SurvivalOverhaul.id("lights_torches"));

    private IgnitionSources() {
    }

    /** Whether a block in the world is burning and can pass its flame on. */
    public static boolean canLightATorch(BlockState state) {
        return state.is(LIGHTS_TORCHES, IgnitionSources::isBurning);
    }

    /**
     * Version-sensitive: {@code BlockState.is(TagKey)} on its own was removed in
     * 26.2, leaving the overload that takes a predicate as well.
     *
     * <p>Blocks with no {@code lit} property, such as fire itself, are always
     * burning. The rest have to say so.
     */
    private static boolean isBurning(BlockBehaviour.BlockStateBase state) {
        return !state.hasProperty(BlockStateProperties.LIT)
                || state.getValue(BlockStateProperties.LIT);
    }
}
