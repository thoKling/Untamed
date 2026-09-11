package dev.untamed.domain.fire;

/**
 * What one sweep of the index did. Useful for debug commands and for asserting
 * on behaviour in tests.
 *
 * @param inspected    positions observed
 * @param extinguished fire sources put out by rain
 * @param relit        fire sources brought back after drying out
 * @param untracked    positions dropped because nothing of ours was there
 */
public record SweepResult(int inspected, int extinguished, int relit, int untracked) {

    public static final SweepResult EMPTY = new SweepResult(0, 0, 0, 0);

    public boolean changedAnything() {
        return extinguished > 0 || relit > 0;
    }
}
