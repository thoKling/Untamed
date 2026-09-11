package dev.untamed.launcher.domain.minecraft

/**
 * One asset: a name in the game's virtual filesystem, and the digest that is
 * both its identity and its storage location.
 */
data class AssetObject(
    val name: String,
    val hash: String,
    val sizeBytes: Long,
) {
    /** Where this object is stored, relative to the installation directory. */
    val relativePath: String get() = "assets/objects/${hash.take(2)}/$hash"

    /** The path it is served from, which is the same path the store uses. */
    val remotePath: String get() = "${hash.take(2)}/$hash"

    val isWellFormed: Boolean
        get() = hash.length == HASH_LENGTH && hash.all { it in '0'..'9' || it in 'a'..'f' }

    private companion object {
        const val HASH_LENGTH = 40
    }
}

/**
 * The asset index for one version.
 *
 * Assets are content-addressed, which is what makes an installation cheap to
 * keep current: an object already on disk under its own hash is the object the
 * index asked for, so a version bump downloads only what actually changed.
 */
data class AssetIndex(
    val id: String,
    val objects: List<AssetObject>,
) {
    val totalBytes: Long get() = objects.sumOf { it.sizeBytes }

    /** Where the index document itself is stored. */
    val relativePath: String get() = "assets/indexes/$id.json"

    companion object {
        val EMPTY = AssetIndex("", emptyList())
    }
}
