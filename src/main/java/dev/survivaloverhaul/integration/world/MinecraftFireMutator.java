package dev.survivaloverhaul.integration.world;

import dev.survivaloverhaul.domain.fire.FireSourceMutator;
import dev.survivaloverhaul.domain.world.GridPos;
import dev.survivaloverhaul.integration.block.SurvivalTorchBlock;
import dev.survivaloverhaul.integration.block.TorchSubstitution;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Applies rule decisions to a Minecraft level.
 *
 * <p>Presentation lives here rather than in the domain. The hiss and the puff of
 * smoke are how a doused torch reads to a player, and that is a Minecraft
 * concern, not part of the rule.
 */
public final class MinecraftFireMutator implements FireSourceMutator {

    private final ServerLevel level;

    public MinecraftFireMutator(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void extinguish(GridPos pos) {
        setLit(pos, false);
    }

    @Override
    public void relight(GridPos pos) {
        setLit(pos, true);
    }

    private void setLit(GridPos pos, boolean lit) {
        BlockPos blockPos = new BlockPos(pos.x(), pos.y(), pos.z());
        BlockState state = level.getBlockState(blockPos);

        if (!TorchSubstitution.isManaged(state) || state.getValue(SurvivalTorchBlock.LIT) == lit) {
            return;
        }

        // UPDATE_ALL sends the new state to nearby clients and lets the lighting
        // engine react to the changed light level. Nothing else is required.
        level.setBlock(blockPos, state.setValue(SurvivalTorchBlock.LIT, lit), Block.UPDATE_ALL);

        if (lit) {
            level.playSound(null, blockPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
            return;
        }

        level.playSound(null, blockPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 2.4f);
        level.sendParticles(
                ParticleTypes.SMOKE,
                blockPos.getX() + 0.5d,
                blockPos.getY() + 0.7d,
                blockPos.getZ() + 0.5d,
                6,
                0.08d, 0.1d, 0.08d,
                0.01d);
    }
}
