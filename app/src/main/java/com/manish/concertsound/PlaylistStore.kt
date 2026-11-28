package com.manish.concertsound

import android.content.Context

/**
 * Local playlist storage.
 *
 * Layout inside the "playlists" SharedPreferences file:
 *   "names"    -> playlist names in creation order, joined by "\n"
 *   "p_<name>" -> that playlist's song ids, joined by "\n"
 *
 * Names are cleaned on write (newlines removed, max 40 chars) so a name can
 * never break the "\n" encoding or the "p_<name>" key.
 */
object PlaylistStore {
    private const val PREF = "playlists"
    private const val KEY_NAMES = "names"
    private const val SEP = "\n"
    private const val MAX_NAME = 40

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, 0)

    /** Playlist names, in creation order. */
    fun names(ctx: Context): MutableList<String> =
        (prefs(ctx).getString(KEY_NAMES, "") ?: "")
            .split(SEP)
            .filter { it.isNotBlank() }
            .toMutableList()

    /** Song ids of one playlist, in the order they were added. */
    fun ids(ctx: Context, name: String): MutableList<String> =
        (prefs(ctx).getString("p_$name", "") ?: "")
            .split(SEP)
            .filter { it.isNotBlank() }
            .toMutableList()

    /** Fast membership check used while filtering the library. */
    fun songsOf(ctx: Context, name: String): Set<String> = ids(ctx, name).toHashSet()

    fun contains(ctx: Context, name: String, id: String): Boolean = ids(ctx, name).contains(id)

    fun size(ctx: Context, name: String): Int = ids(ctx, name).size

    /** Turns user input into a safe stored name, or null when it is unusable. */
    fun cleanName(raw: String): String? {
        val n = raw.trim().replace("\n", " ").replace("\r", " ").take(MAX_NAME).trim()
        return n.takeIf { it.isNotEmpty() }
    }

    /** Returns true when the playlist was created, false when the name is bad or taken. */
    fun create(ctx: Context, raw: String): Boolean {
        val name = cleanName(raw) ?: return false
        val list = names(ctx)
        if (list.any { it.equals(name, ignoreCase = true) }) return false
        list.add(name)
        prefs(ctx).edit()
            .putString(KEY_NAMES, list.joinToString(SEP))
            .putString("p_$name", "")
            .apply()
        return true
    }

    /** Returns false when the new name is bad or already used by another playlist. */
    fun rename(ctx: Context, old: String, raw: String): Boolean {
        val name = cleanName(raw) ?: return false
        if (name == old) return true
        val list = names(ctx)
        val at = list.indexOf(old)
        if (at < 0) return false
        if (list.any { it.equals(name, ignoreCase = true) }) return false
        list[at] = name
        prefs(ctx).edit()
            .putString(KEY_NAMES, list.joinToString(SEP))
            .putString("p_$name", ids(ctx, old).joinToString(SEP))
            .remove("p_$old")
            .apply()
        return true
    }

    fun delete(ctx: Context, name: String) {
        val list = names(ctx)
        if (!list.remove(name)) return
        prefs(ctx).edit()
            .putString(KEY_NAMES, list.joinToString(SEP))
            .remove("p_$name")
            .apply()
    }

    /** Adds a song once; re-adding the same song does nothing. */
    fun addSong(ctx: Context, name: String, id: String) {
        val l = ids(ctx, name)
        if (!l.contains(id)) {
            l.add(id)
            prefs(ctx).edit().putString("p_$name", l.joinToString(SEP)).apply()
        }
    }

    fun removeSong(ctx: Context, name: String, id: String) {
        val l = ids(ctx, name)
        if (l.remove(id)) {
            prefs(ctx).edit().putString("p_$name", l.joinToString(SEP)).apply()
        }
    }

    /** Playlists that already hold this song, in creation order. */
    fun playlistsWith(ctx: Context, id: String): List<String> =
        names(ctx).filter { contains(ctx, it, id) }
}
