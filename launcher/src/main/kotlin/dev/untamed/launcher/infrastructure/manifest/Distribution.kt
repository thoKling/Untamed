package dev.untamed.launcher.infrastructure.manifest

/**
 * Where the product publishes its own release manifest.
 *
 * Separate from `Endpoints`, which holds Mojang's and Fabric's addresses. Those
 * are fixed forever and this one is not: it is what the launcher asks for a
 * newer release, and a user testing a build can point the launcher somewhere
 * else through a setting.
 */
object Distribution {

    /**
     * The published manifest for the stable channel.
     *
     * A placeholder host until the distribution site exists. Until then the
     * remote fetch fails, the bundled copy answers, and the launcher reports
     * the versions it shipped with, which is exactly the behaviour an outage
     * is meant to produce.
     */
    const val MANIFEST_URL = "https://downloads.untamed.invalid/manifest.json"
}
