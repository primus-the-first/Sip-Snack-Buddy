package com.sipsnack.buddy.rules

object Versions {
    /** True when a release tag like "v0.2.2" is a higher version than the installed "0.2.1". */
    fun isNewer(tag: String, installed: String): Boolean {
        val a = parts(tag) ?: return false
        val b = parts(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int>? =
        v.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
}
