package dev.untamed.launcher.infrastructure.account

/**
 * The Azure application registration this launcher signs in as.
 *
 * The Minecraft services API only answers clients whose Azure AD application
 * Mojang has approved for use with the launcher API. That approval is an
 * application step with a lead time, not a code step, so the whole sign-in is
 * written against a client id that a build supplies rather than against one
 * hard-coded here.
 *
 * A public client with no secret is the correct shape for a desktop
 * application: a secret shipped to users is not a secret, which is exactly what
 * PKCE exists to replace. So the client id below is not sensitive. It is an
 * identifier, it appears in the browser's address bar during every sign-in, and
 * nothing is protected by it being absent.
 *
 * Until a registration exists, [isConfigured] is false and the launcher says so
 * in a sentence rather than failing somewhere in the middle of the chain.
 */
object AzureApplication {

    /**
     * The environment variable a developer with an approved registration sets,
     * so a build can be tried without editing source or shipping an id.
     */
    const val CLIENT_ID_VARIABLE = "UNTAMED_CLIENT_ID"

    /**
     * The registration this launcher signs in as. Blank means not configured.
     *
     * Present here rather than only in the environment variable because a
     * public client id is an identifier and not a secret: it travels in the
     * browser's address bar on every sign-in, and nothing is protected by a
     * build having to be told it separately.
     */
    private const val BUILT_IN_CLIENT_ID = "b7fa41f7-064d-4a19-9ffb-06d49731276b"

    val clientId: String
        get() = System.getenv(CLIENT_ID_VARIABLE)?.trim().orEmpty().ifBlank { BUILT_IN_CLIENT_ID }

    val isConfigured: Boolean get() = clientId.isNotBlank()

    /**
     * What the user is told when there is no registration.
     *
     * It says the launcher is unfinished rather than that sign-in failed,
     * because those call for different things from the person reading it.
     */
    const val NOT_CONFIGURED =
        "Microsoft sign-in is not available in this build: it has no approved Azure " +
            "application registration yet."
}
