package dev.untamed.launcher.application.installation

/**
 * The published manifest, with the bundled copy behind it.
 *
 * A distribution outage should degrade to "this launcher cannot tell you about
 * a newer release" and not to "this launcher does not start". The bundled copy
 * describes the release this build shipped against, which is a version that is
 * known to work, so falling back to it is a step backwards in freshness and
 * never a step backwards in safety.
 *
 * A rejected remote manifest falls back too, not only an unreachable one. A
 * document that fails validation is either corrupt or hostile, and in both
 * cases the pinned copy inside the launcher is the better of the two. The one
 * thing that does not happen is acting on the parts of it that validated.
 *
 * [onFallback] is how the composition root gets to log this. The port stays
 * free of the logger, so the decision itself can be tested without one.
 */
class FallbackManifestSource(
    private val primary: ManifestSource,
    private val fallback: ManifestSource,
    private val onFallback: (String) -> Unit = {},
) : ManifestSource {

    override fun load(): ManifestResult {
        val remote = primary.load()
        if (remote is ManifestResult.Loaded) return remote

        onFallback("${remote.origin} is unusable: ${describe(remote)}")
        val local = fallback.load()
        return when (local) {
            // The origin is what the diagnostics report and the settings screen
            // show, so it has to say that this is not the current manifest but
            // the one in the box.
            is ManifestResult.Loaded -> local.copy(origin = "${local.origin} (offline copy)")
            else -> local
        }
    }

    private fun describe(result: ManifestResult): String = when (result) {
        is ManifestResult.Loaded -> "loaded"
        is ManifestResult.Unavailable -> result.reason
        is ManifestResult.Rejected -> result.problems.joinToString("; ")
    }
}
