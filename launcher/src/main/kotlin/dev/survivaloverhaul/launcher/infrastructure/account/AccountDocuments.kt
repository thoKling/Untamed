package dev.survivaloverhaul.launcher.infrastructure.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire shapes of the six services a sign-in passes through.
 *
 * Kept apart from the domain for the same reason the version documents are:
 * these are documents someone else publishes, and every field crosses one
 * explicit mapping before the rest of the launcher acts on it. Nothing here is
 * ever logged. Three of these types carry a token, and a data class that prints
 * its fields is one careless log line away from putting one in a file, which is
 * why each of them overrides [toString].
 */
object AccountDocuments {

    val json: Json = Json { ignoreUnknownKeys = true }

    fun microsoftToken(text: String): MicrosoftTokenDocument = json.decodeFromString(text)

    fun xboxToken(text: String): XboxTokenDocument = json.decodeFromString(text)

    fun minecraftToken(text: String): MinecraftTokenDocument = json.decodeFromString(text)

    fun entitlements(text: String): EntitlementsDocument = json.decodeFromString(text)

    fun profile(text: String): ProfileDocument = json.decodeFromString(text)

    /**
     * An error body, whatever shape of error it is.
     *
     * The three services in the chain each have their own, and a failure is a
     * bad enough moment without the launcher deciding it cannot read the reason
     * because the reason came from Xbox rather than from Microsoft. Every field
     * is optional and the caller reads the one its step produces.
     */
    fun error(text: String): ErrorDocument =
        runCatching { json.decodeFromString<ErrorDocument>(text) }.getOrDefault(ErrorDocument())
}

/** Microsoft's OAuth token response. Both tokens are secrets. */
@Serializable
data class MicrosoftTokenDocument(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresInSeconds: Long = 0,
) {
    override fun toString(): String = "MicrosoftTokenDocument(expires in ${expiresInSeconds}s)"
}

/**
 * Xbox Live's and XSTS's response, which are the same shape.
 *
 * The user hash in `DisplayClaims` is what the Minecraft login wants alongside
 * the token, and the XUID in the same claim is what the game is started with.
 */
@Serializable
data class XboxTokenDocument(
    @SerialName("Token") val token: String = "",
    @SerialName("DisplayClaims")
    val displayClaims: DisplayClaimsDocument = DisplayClaimsDocument(),
) {
    val userHash: String get() = displayClaims.xui.firstOrNull()?.userHash.orEmpty()

    val xuid: String get() = displayClaims.xui.firstOrNull()?.xuid.orEmpty()

    override fun toString(): String =
        "XboxTokenDocument(user hash present=${userHash.isNotBlank()})"
}

@Serializable
data class DisplayClaimsDocument(
    val xui: List<XuiClaimDocument> = emptyList(),
)

@Serializable
data class XuiClaimDocument(
    @SerialName("uhs") val userHash: String = "",
    @SerialName("xid") val xuid: String = "",
)

/** The Minecraft services token. Roughly a day's lifetime, memory only. */
@Serializable
data class MinecraftTokenDocument(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expires_in") val expiresInSeconds: Long = 0,
) {
    override fun toString(): String = "MinecraftTokenDocument(expires in ${expiresInSeconds}s)"
}

/**
 * What the account is entitled to.
 *
 * Ownership is "this list is not empty" rather than a match against a set of
 * product names. Java Edition arrives under several of them depending on how it
 * was bought, Game Pass adds its own, and Mojang adds more when a new way to
 * own the game appears. An account that owns nothing gets an empty list, which
 * is the case this check exists to catch, and a launcher that refuses a
 * legitimately owned copy because the product name is new is worse than one
 * that trusts Mojang's own answer.
 */
@Serializable
data class EntitlementsDocument(
    val items: List<EntitlementDocument> = emptyList(),
) {
    fun ownsJavaEdition(): Boolean = items.isNotEmpty()

    /** For the log. Product names are not secrets and say what was found. */
    fun describe(): String =
        items.joinToString { it.name }.ifBlank { "no entitlements" }
}

@Serializable
data class EntitlementDocument(val name: String = "")

/**
 * The player as the game knows them: the undashed UUID and the name. Neither is
 * a secret; every server the player joins is told both.
 */
@Serializable
data class ProfileDocument(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class ErrorDocument(
    val error: String = "",
    @SerialName("error_description") val errorDescription: String = "",
    @SerialName("errorMessage") val errorMessage: String = "",
    @SerialName("XErr") val xboxErrorCode: Long? = null,
) {
    /** The first of the three that is filled in, or nothing. */
    fun message(): String = listOf(errorDescription, errorMessage, error)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}
