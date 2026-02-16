package com.fdroid.cryptomonitor.update

object VersionComparator {
    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = parse(candidate)
        val currentParts = parse(current)
        val max = maxOf(candidateParts.size, currentParts.size)

        for (i in 0 until max) {
            val c = candidateParts.getOrElse(i) { 0 }
            val p = currentParts.getOrElse(i) { 0 }
            if (c != p) return c > p
        }
        return false
    }

    private fun parse(version: String): List<Int> {
        return Regex("\\d+")
            .findAll(version)
            .map { it.value.toIntOrNull() ?: 0 }
            .toList()
            .ifEmpty { listOf(0) }
    }
}
