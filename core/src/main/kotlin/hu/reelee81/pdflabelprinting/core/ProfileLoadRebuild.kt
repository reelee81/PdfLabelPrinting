package hu.reelee81.pdflabelprinting.core

object ProfileLoadRebuild {
    fun isNeeded(
        built: NupConfig?,
        loaded: NupConfig,
        positions: IntArray,
        oldDefaultPosition: Int,
        newDefaultPosition: Int
    ): Boolean =
        built != loaded ||
            oldDefaultPosition != newDefaultPosition ||
            positions.any { it != newDefaultPosition }
}
