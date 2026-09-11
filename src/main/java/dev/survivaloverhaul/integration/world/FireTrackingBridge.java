package dev.survivaloverhaul.integration.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * One-way hand-off from block callbacks to whichever layer is tracking fire
 * sources.
 *
 * <p>Blocks live in the integration layer and must not reach up into the server
 * layer, so the server registers a listener here at start-up instead. If nothing
 * registers, the calls are no-ops and the blocks still work.
 */
public final class FireTrackingBridge {

    /** Receives notifications about fire sources appearing or changing by hand. */
    public interface Listener {

        void onFireSourcePlaced(Level level, BlockPos pos);

        void onFireSourceRelitByHand(Level level, BlockPos pos);
    }

    private static final Listener NONE = new Listener() {
        @Override
        public void onFireSourcePlaced(Level level, BlockPos pos) {
        }

        @Override
        public void onFireSourceRelitByHand(Level level, BlockPos pos) {
        }
    };

    private static volatile Listener listener = NONE;

    private FireTrackingBridge() {
    }

    public static void setListener(Listener newListener) {
        listener = newListener == null ? NONE : newListener;
    }

    public static void clearListener() {
        listener = NONE;
    }

    public static void fireSourcePlaced(Level level, BlockPos pos) {
        listener.onFireSourcePlaced(level, pos);
    }

    public static void fireSourceRelitByHand(Level level, BlockPos pos) {
        listener.onFireSourceRelitByHand(level, pos);
    }
}
