package dev.survivaloverhaul.integration.registry;

import dev.survivaloverhaul.SurvivalOverhaul;
import dev.survivaloverhaul.integration.block.SurvivalTorchBlock;
import dev.survivaloverhaul.integration.block.SurvivalWallTorchBlock;
import dev.survivaloverhaul.integration.item.UnlitTorchItem;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registration of the mod's blocks and their items.
 *
 * <p>The lit torch item is deliberately absent from the creative menu. Players
 * obtain and place ordinary vanilla torches, and the placement mixin swaps in
 * this block; a second lit torch in the menu would only be confusing. The block
 * item still exists so pick-block and structure handling behave.
 *
 * <p>The unlit torch is different: it is what the crafting recipe yields and what
 * a doused torch drops, so it is a real item a player holds and it does appear in
 * the menu, next to the vanilla torch.
 */
public final class ModBlocks {

    public static final String TORCH_ID = "torch";
    public static final String WALL_TORCH_ID = "wall_torch";
    public static final String UNLIT_TORCH_ID = "unlit_torch";

    public static final SurvivalTorchBlock TORCH = register(
            TORCH_ID,
            SurvivalTorchBlock::new,
            torchProperties());

    public static final SurvivalWallTorchBlock WALL_TORCH = register(
            WALL_TORCH_ID,
            SurvivalWallTorchBlock::new,
            torchProperties());

    public static final Item TORCH_ITEM = registerTorchItem();

    public static final Item UNLIT_TORCH_ITEM = registerUnlitTorchItem();

    private ModBlocks() {
    }

    /**
     * Shared block properties for every torch variant.
     *
     * <p>The light-level function is the whole reason the lit state works. When
     * {@code lit} flips, Minecraft sees the emitted light change and re-lights
     * the area itself, so the mod never calls the lighting engine directly.
     */
    private static BlockBehaviour.Properties torchProperties() {
        return BlockBehaviour.Properties.of()
                .noCollision()
                .instabreak()
                .lightLevel(ModBlocks::torchLightLevel)
                .sound(SoundType.WOOD)
                .pushReaction(PushReaction.DESTROY)
                .mapColor(MapColor.FIRE);
    }

    private static int torchLightLevel(BlockState state) {
        return state.getValue(SurvivalTorchBlock.LIT) ? SurvivalTorchBlock.LIT_LUMINANCE : 0;
    }

    private static <T extends Block> T register(String name,
                                                Function<BlockBehaviour.Properties, T> factory,
                                                BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SurvivalOverhaul.id(name));
        T block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    private static Item registerTorchItem() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SurvivalOverhaul.id(TORCH_ID));
        Item item = new StandingAndWallBlockItem(
                TORCH,
                WALL_TORCH,
                Direction.DOWN,
                new Item.Properties().useBlockDescriptionPrefix().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static Item registerUnlitTorchItem() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SurvivalOverhaul.id(UNLIT_TORCH_ID));
        Item item = new UnlitTorchItem(
                TORCH,
                WALL_TORCH,
                Direction.DOWN,
                new Item.Properties().useBlockDescriptionPrefix().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    /** Entry point from the mod initialiser; forces this class to load. */
    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output ->
                output.insertAfter(Items.TORCH, UNLIT_TORCH_ITEM));

        SurvivalOverhaul.LOGGER.debug("Registered survival fire blocks");
    }
}
