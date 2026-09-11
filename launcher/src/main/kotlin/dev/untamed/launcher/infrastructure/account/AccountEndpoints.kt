package dev.untamed.launcher.infrastructure.account

/**
 * Every address a sign-in touches, in one file.
 *
 * Constants, like Mojang's and Fabric's download addresses, and for a stronger
 * reason: these are where a Microsoft account is presented. Nothing the
 * launcher downloads, and no setting, can redirect any of them. If an address
 * here is wrong, it is wrong in the source and in review, not at runtime.
 *
 * The `consumers` tenant is deliberate. Minecraft: Java Edition accounts are
 * personal Microsoft accounts, and pointing at `common` would offer a work or
 * school account that can never own the game.
 */
object AccountEndpoints {

    const val AUTHORIZE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"

    const val TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

    const val XBOX_AUTHENTICATE = "https://user.auth.xboxlive.com/user/authenticate"

    const val XSTS_AUTHORIZE = "https://xsts.auth.xboxlive.com/xsts/authorize"

    const val MINECRAFT_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox"

    const val ENTITLEMENTS = "https://api.minecraftservices.com/entitlements/mcstore"

    const val PROFILE = "https://api.minecraftservices.com/minecraft/profile"

    /**
     * The only scope asked for. `XboxLive.signin` is what the chain needs and
     * `offline_access` is what makes a refresh token possible. Nothing else is
     * requested, because a consent screen listing permissions the launcher does
     * not use is a launcher asking to be distrusted.
     */
    const val SCOPE = "XboxLive.signin offline_access"

    /** Who the XSTS token is for. Minecraft services accepts no other. */
    const val RELYING_PARTY = "rp://api.minecraftservices.com/"
}
