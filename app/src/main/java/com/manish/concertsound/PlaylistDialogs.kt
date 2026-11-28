package com.manish.concertsound

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Plain-View dialogs for playlists: create, open, play, rename, delete,
 * and "add this song to a playlist".
 */
object PlaylistDialogs {

    private class RowViews(val root: LinearLayout, val play: TextView)

    private fun dp(a: Activity, v: Int) = (v * a.resources.displayMetrics.density).toInt()

    private fun shape(a: Activity, color: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(a, r).toFloat()
        if (stroke != 0) g.setStroke(dp(a, 1), stroke)
        return g
    }

    private fun tv(a: Activity, s: String, size: Float, color: Int, face: String = "sans-serif"): TextView {
        val t = TextView(a)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        t.typeface = android.graphics.Typeface.create(face, android.graphics.Typeface.NORMAL)
        return t
    }

    private fun toast(a: Context, s: String) = Toast.makeText(a, s, Toast.LENGTH_SHORT).show()

    private fun row(a: Activity, main: String, sub: String, showPlay: Boolean): RowViews {
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(a, 16), dp(a, 12), dp(a, 10), dp(a, 12))
        r.background = shape(a, Pal.SURF, 18, Pal.LINE)
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        val t = tv(a, main, 16f, Pal.TEXT, "sans-serif-medium")
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        val s = tv(a, sub, 12f, Pal.MUTE)
        s.maxLines = 1
        s.ellipsize = TextUtils.TruncateAt.END
        col.addView(t)
        col.addView(s)
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val p = tv(a, if (showPlay) "▶" else "", 20f, Pal.A2)
        p.setPadding(dp(a, 12), dp(a, 4), dp(a, 4), dp(a, 4))
        r.addView(p)
        return RowViews(r, p)
    }

    private fun addRow(a: Activity, box: LinearLayout, r: LinearLayout, top: Int) {
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(a, top)
        box.addView(r, lp)
    }

    private fun buildDialog(a: Activity, title: String, box: LinearLayout): AlertDialog {
        val sc = ScrollView(a)
        sc.isVerticalScrollBarEnabled = false
        sc.overScrollMode = View.OVER_SCROLL_NEVER
        sc.addView(box)
        return AlertDialog.Builder(a)
            .setTitle(title)
            .setView(sc)
            .setNegativeButton("Close", null)
            .create()
    }

    private fun askName(a: Activity, title: String, pre: String, onOk: (String) -> Unit) {
        val e = EditText(a)
        e.setText(pre)
        e.hint = "Playlist ka naam"
        e.setHintTextColor(Pal.MUTE)
        e.setTextColor(Pal.TEXT)
        e.textSize = 16f
        e.inputType = InputType.TYPE_CLASS_TEXT
        e.isSingleLine = true
        val wrap = LinearLayout(a)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(dp(a, 20), dp(a, 10), dp(a, 20), 0)
        wrap.addView(e)
        AlertDialog.Builder(a)
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("Save") { _, _ -> onOk(e.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun actions(
        a: Activity,
        name: String,
        close: () -> Unit,
        onOpen: (String?) -> Unit,
        onPlay: (String) -> Unit
    ) {
        val opts = arrayOf("Play", "Rename", "Delete")
        AlertDialog.Builder(a)
            .setTitle(name)
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> {
                        close()
                        onPlay(name)
                    }
                    1 -> {
                        close()
                        askName(a, "Rename playlist", name) { raw ->
                            if (!PlaylistStore.rename(a, name, raw)) toast(a, "Naam dusra rakho.")
                        }
                    }
                    else -> {
                        close()
                        AlertDialog.Builder(a)
                            .setTitle("Delete \"$name\"?")
                            .setMessage("Playlist delete ho jayegi. Gaane phone me rahenge.")
                            .setPositiveButton("Delete") { _, _ ->
                                PlaylistStore.delete(a, name)
                                onOpen(null)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    /**
     * Playlist list. onOpen(null) means "show all songs again".
     * onPlay(name) starts that playlist from the first song.
     */
    fun showManager(a: Activity, onOpen: (String?) -> Unit, onPlay: (String) -> Unit) {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(a, 4), dp(a, 6), dp(a, 4), dp(a, 6))

        var dlg: AlertDialog? = null
        val close = { dlg?.dismiss() }
        val refresh = {
            dlg?.dismiss()
            showManager(a, onOpen, onPlay)
        }

        val all = row(a, "All songs", "Playlist view band karo", false)
        all.root.setOnClickListener {
            close()
            onOpen(null)
        }
        addRow(a, box, all.root, 0)

        for (name in PlaylistStore.names(a)) {
            val n = PlaylistStore.size(a, name)
            val r = row(a, name, if (n == 1) "1 song" else "$n songs", true)
            r.root.setOnClickListener {
                close()
                onOpen(name)
            }
            r.play.setOnClickListener {
                close()
                onPlay(name)
            }
            r.root.setOnLongClickListener {
                actions(a, name, close, onOpen, onPlay)
                true
            }
            addRow(a, box, r.root, 8)
        }

        val new = row(a, "+ New playlist", "Naya playlist banao", false)
        new.root.setOnClickListener {
            close()
            askName(a, "New playlist", "") { raw ->
                val made = PlaylistStore.cleanName(raw)
                if (made == null || !PlaylistStore.create(a, raw)) {
                    toast(a, "Naam khali nahi ho sakta, aur dusra naam ho.")
                } else {
                    toast(a, "\"$made\" ban gayi")
                    refresh()
                }
            }
        }
        addRow(a, box, new.root, 8)

        dlg = buildDialog(a, "Playlists", box)
        dlg?.show()
    }

    /** "Add this song to a playlist" chooser. */
    fun addToPlaylist(a: Activity, id: String, title: String) {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(a, 4), dp(a, 6), dp(a, 4), dp(a, 6))

        for (name in PlaylistStore.names(a)) {
            val has = PlaylistStore.contains(a, name, id)
            val r = row(a, name, if (has) "Isme pehle se hai" else "Add karo", false)
            r.root.setOnClickListener {
                PlaylistStore.addSong(a, name, id)
                toast(a, if (has) "\"$name\" me already hai" else "\"$name\" me add ho gaya")
            }
            addRow(a, box, r.root, 8)
        }

        val new = row(a, "+ New playlist", "Naya playlist bana ke add karo", false)
        new.root.setOnClickListener {
            askName(a, "New playlist", "") { raw ->
                val made = PlaylistStore.cleanName(raw)
                if (made == null || !PlaylistStore.create(a, raw)) {
                    toast(a, "Naam khali nahi ho sakta, aur dusra naam ho.")
                } else {
                    PlaylistStore.addSong(a, made, id)
                    toast(a, "\"$made\" me add ho gaya")
                }
            }
        }
        addRow(a, box, new.root, 8)

        buildDialog(a, "Add to playlist: ${title.take(24)}", box).show()
    }
}
