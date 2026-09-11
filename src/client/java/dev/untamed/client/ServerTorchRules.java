package dev.untamed.client;

/**
 * The torch rules the connected server reported, held for presentation only.
 *
 * <p>Client-side copies of server state are always a cache, never a source of
 * truth. Nothing the client does with this value can change what happens in the
 * world.
 */
public final class ServerTorchRules {

    private static volatile boolean dousingEnabled;
    private static volatile boolean relightWhenDry;

    private ServerTorchRules() {
    }

    public static void update(boolean dousing, boolean relight) {
        dousingEnabled = dousing;
        relightWhenDry = relight;
    }

    /** Resets to a neutral state on disconnect, so stale rules are never shown. */
    public static void reset() {
        dousingEnabled = false;
        relightWhenDry = false;
    }

    public static boolean dousingEnabled() {
        return dousingEnabled;
    }

    public static boolean relightWhenDry() {
        return relightWhenDry;
    }
}
