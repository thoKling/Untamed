package dev.untamed.domain.fire;

/**
 * Everything the dousing rules need to know about one tracked position.
 *
 * <p>The integration layer gathers this from the world in a single pass, so the
 * rules themselves never reach back into Minecraft.
 *
 * @param chunkLoaded   whether the containing chunk is currently loaded
 * @param present       whether a survival fire source still occupies the position
 * @param lit           whether that fire source is burning
 * @param rainedOn      whether precipitation is currently falling on the position
 * @param thundering    whether the world is in a thunderstorm
 * @param dousedByRain  whether this position was extinguished by rain rather than by hand
 */
public record FireObservation(
        boolean chunkLoaded,
        boolean present,
        boolean lit,
        boolean rainedOn,
        boolean thundering,
        boolean dousedByRain
) {

    private static final FireObservation UNLOADED =
            new FireObservation(false, false, false, false, false, false);
    private static final FireObservation ABSENT =
            new FireObservation(true, false, false, false, false, false);

    /** The chunk went away between indexing and the sweep. */
    public static FireObservation unloaded() {
        return UNLOADED;
    }

    /** The block is gone, so the position should leave the index. */
    public static FireObservation absent() {
        return ABSENT;
    }
}
