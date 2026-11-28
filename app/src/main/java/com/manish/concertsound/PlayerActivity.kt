package com.manish.concertsound

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class Song(
    val id: Long, val title: String, val artist: String,
    val durationMs: Long, val uri: Uri, val artUri: Uri
)

class IconView(c: Context, var kind: Int, var tint: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun set(k: Int) { kind = k; invalidate() }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = tint
        when (kind) {
            0 -> {
                val a = Path()
                a.moveTo(w * 0.34f, h * 0.2f); a.lineTo(w * 0.8f, h * 0.5f)
                a.lineTo(w * 0.34f, h * 0.8f); a.close()
                cv.drawPath(a, p)
            }
            1 -> {
                cv.drawRoundRect(RectF(w * 0.3f, h * 0.22f, w * 0.46f, h * 0.78f), 6f, 6f, p)
                cv.drawRoundRect(RectF(w * 0.54f, h * 0.22f, w * 0.7f, h * 0.78f), 6f, 6f, p)
            }
            2 -> {
                val a = Path()
                a.moveTo(w * 0.24f, h * 0.26f); a.lineTo(w * 0.62f, h * 0.5f)
                a.lineTo(w * 0.24f, h * 0.74f); a.close()
                cv.drawPath(a, p)
                cv.drawRoundRect(RectF(w * 0.68f, h * 0.26f, w * 0.77f, h * 0.74f), 4f, 4f, p)
            }
            else -> {
                val a = Path()
                a.moveTo(w * 0.76f, h * 0.26f); a.lineTo(w * 0.38f, h * 0.5f)
                a.lineTo(w * 0.76f, h * 0.74f); a.close()
                cv.drawPath(a, p)
                cv.drawRoundRect(RectF(w * 0.23f, h * 0.26f, w * 0.32f, h * 0.74f), 4f, 4f, p)
            }
        }
    }
}

class PlayerActivity : Activity() {
    private class Row(
        val root: LinearLayout, val img: ImageView,
        val title: TextView, val artist: TextView, val star: TextView
    )

    private var songs: List<Song> = emptyList()
    private var shown: List<Song> = emptyList()
    private var query = ""
    private var onlyLiked = false
    private var currentId = -1L
    private var seeking = false
    private var topColor = Pal.BG
    private lateinit var liked: HashSet<String>

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private lateinit var adapter: Adapter
    private lateinit var empty: TextView
    private lateinit var chipAll: TextView
    private lateinit var chipLiked: TextView
    private lateinit var mini: LinearLayout
    private lateinit var miniImg: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: IconView
    private lateinit var full: LinearLayout
    private lateinit var fullBg: GradientDrawable
    private lateinit var fullImg: ImageView
    private lateinit var fullTitle: TextView
    private lateinit var fullArtist: TextView
    private lateinit var fullStar: TextView
    private lateinit var fullPlay: IconView
    private lateinit var slim: Slim
    private lateinit var tPos: TextView
    private lateinit var tDur: TextView
    private lateinit var chipShuffle: TextView
    private lateinit var chipRepeat: TextView
        private var fullOpen = false
    private var openPlaylist: String? = null
    private lateinit var chipPlaylists: TextView
    
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, r: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(r).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    private fun tv(s: String, size: Float, color: Int, face: String = "sans-serif"): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        t.typeface = Typeface.create(face, Typeface.NORMAL)
        return t
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
    }

    private fun press(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    x.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
    }

    private fun art(s: Song, px: Int, cb: (Bitmap?) -> Unit) {
        val key = "${s.id}_$px"
        cache.get(key)?.let { cb(it); return }
        pool.execute {
            val b: Bitmap? = try {
                if (Build.VERSION.SDK_INT >= 29) {
                    contentResolver.loadThumbnail(s.uri, Size(px, px), null)
                } else {
                    contentResolver.openInputStream(s.artUri)?.use { BitmapFactory.decodeStream(it) }
                }
            } catch (e: Exception) { null }
            val r2 = if (b != null && b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
            if (r2 != null) cache.put(key, r2)
            ui.post { cb(r2) }
        }
    }

    private fun avg(b: Bitmap): Int = try {
        Bitmap.createScaledBitmap(b.copy(Bitmap.Config.ARGB_8888, false), 1, 1, true).getPixel(0, 0)
    } catch (e: Exception) { Pal.SURF }

    private fun tint(c: Int) {
        val to = lerpC(c, Pal.BG, 0.45f)
        val from = topColor
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 700
        a.addUpdateListener {
            val col = lerpC(from, to, it.animatedValue as Float)
            fullBg.colors = intArrayOf(col, Pal.BG)
            topColor = col
        }
        a.start()
    }

    // ---------- library ----------
    private fun loadSongs() {
        Thread {
            val out = ArrayList<Song>()
            val m = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION
            )
            val sel = "${MediaStore.Audio.Media.DURATION} > 30000 AND " +
                "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
                "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
                "${MediaStore.Audio.Media.IS_ALARM} = 0"
            try {
                contentResolver.query(m, proj, sel, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val artist = c.getString(2)?.takeIf { it != "<unknown>" } ?: "Unknown artist"
                        val album = c.getLong(3)
                        out.add(
                            Song(
                                id, c.getString(1) ?: "Unknown", artist, c.getLong(4),
                                ContentUris.withAppendedId(m, id),
                                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), album)
                            )
                        )
                    }
                }
            } catch (e: Exception) {}
            ui.post { songs = out; refilter() }
        }.start()
    }

        private fun refilter() {
        val q = query.trim().lowercase()
        val keep = openPlaylist?.let { PlaylistStore.songsOf(this, it) }
        shown = songs.filter {
            (keep == null || keep.contains(it.id.toString())) &&
                (!onlyLiked || liked.contains(it.id.toString())) &&
                (q.isEmpty() || it.title.lowercase().contains(q) || it.artist.lowercase().contains(q))
        }
        adapter.notifyDataSetChanged()
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        }
        

    private fun toggleLike(s: Song) {
        val k = s.id.toString()
        if (!liked.remove(k)) liked.add(k)
        getSharedPreferences("player", 0).edit().putStringSet("liked", HashSet(liked)).apply()
        if (onlyLiked) refilter() else adapter.notifyDataSetChanged()
        updateNow()
    }

    private fun paintChips() {
        chipAll.background = shape(if (!onlyLiked) Pal.TEXT else Pal.SURF, 20, Pal.LINE)
        chipAll.setTextColor(if (!onlyLiked) Pal.BG else Pal.MUTE)
        chipLiked.background = shape(if (onlyLiked) Pal.TEXT else Pal.SURF, 20, Pal.LINE)
        chipLiked.setTextColor(if (onlyLiked) Pal.BG else Pal.MUTE)
    }

    inner class Adapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = shown[position].id

        private fun makeRow(): Row {
            val root = LinearLayout(this@PlayerActivity)
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.setPadding(0, dp(8), 0, dp(8))
            val img = ImageView(this@PlayerActivity)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.background = shape(Pal.LINE, 12)
            img.clipToOutline = true
            root.addView(img, LinearLayout.LayoutParams(dp(54), dp(54)))
            val col = LinearLayout(this@PlayerActivity)
            col.orientation = LinearLayout.VERTICAL
            val t = tv("", 16f, Pal.TEXT)
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            val a = tv("", 13f, Pal.MUTE)
            a.maxLines = 1
            a.ellipsize = TextUtils.TruncateAt.END
            col.addView(t)
            col.addView(a)
            val cl = LinearLayout.LayoutParams(0, -2, 1f)
            cl.leftMargin = dp(14)
            root.addView(col, cl)
            val st = tv("★", 22f, Pal.MUTE)
            st.setPadding(dp(12), dp(8), dp(4), dp(8))
            root.addView(st)
            val r = Row(root, img, t, a, st)
            root.tag = r
            return r
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val r = (convertView?.tag as? Row) ?: makeRow()
            val s = shown[position]
            r.title.text = s.title
            r.artist.text = s.artist
            r.title.setTextColor(if (s.id == currentId) Pal.A1 else Pal.TEXT)
            r.star.setTextColor(if (liked.contains(s.id.toString())) Pal.A2 else Pal.LINE)
            r.star.setOnClickListener { toggleLike(s) }
            r.img.tag = s.id
            r.img.setImageDrawable(null)
            art(s, 160) { b -> if (r.img.tag == s.id && b != null) r.img.setImageBitmap(b) }
            r.root.setOnClickListener { playAt(shown, position) }
            return r.root
        }
    }

    // ---------- playback ----------
    private fun playAt(list: List<Song>, index: Int) {
        val c = controller
        if (c == null) {
            Toast.makeText(this, "Player taiyaar ho raha hai, dobara dabao.", Toast.LENGTH_SHORT).show()
            return
        }
        val items = list.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title).setArtist(s.artist).setArtworkUri(s.artUri).build()
                ).build()
        }
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    private fun updateIcons() {
        val playing = controller?.isPlaying == true
        miniPlay.set(if (playing) 1 else 0)
        fullPlay.set(if (playing) 1 else 0)
    }

    private fun updateModes() {
        val c = controller ?: return
        val sh = c.shuffleModeEnabled
        chipShuffle.background = shape(if (sh) Pal.TEXT else Color.argb(60, 255, 255, 255), 20)
        chipShuffle.setTextColor(if (sh) Pal.BG else Pal.TEXT)
        val rp = c.repeatMode
        chipRepeat.text = when (rp) {
            Player.REPEAT_MODE_ONE -> "Repeat 1"
            Player.REPEAT_MODE_ALL -> "Repeat all"
            else -> "Repeat off"
        }
        chipRepeat.background = shape(
            if (rp != Player.REPEAT_MODE_OFF) Pal.TEXT else Color.argb(60, 255, 255, 255), 20
        )
        chipRepeat.setTextColor(if (rp != Player.REPEAT_MODE_OFF) Pal.BG else Pal.TEXT)
    }

    private fun updateNow() {
        val c = controller ?: return
        val item = c.currentMediaItem
        if (item == null) { mini.visibility = View.GONE; return }
        val id = item.mediaId.toLongOrNull() ?: return
        val s = songs.firstOrNull { it.id == id } ?: return
        val changed = currentId != id
        currentId = id
        mini.visibility = View.VISIBLE
        miniTitle.text = s.title
        miniArtist.text = s.artist
        fullTitle.text = s.title
        fullArtist.text = s.artist
        fullStar.setTextColor(if (liked.contains(id.toString())) Pal.A2 else Pal.MUTE)
        updateIcons()
        updateModes()
        if (changed) {
            adapter.notifyDataSetChanged()
            miniImg.setImageDrawable(null)
            fullImg.setImageDrawable(null)
            art(s, 160) { b -> if (currentId == id && b != null) miniImg.setImageBitmap(b) }
            art(s, 800) { b ->
                if (currentId == id && b != null) {
                    fullImg.setImageBitmap(b)
                    tint(avg(b))
                }
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null && !seeking) {
                val d = c.duration
                val p = c.currentPosition
                if (d > 0) {
                    slim.value = (p * 1000 / d).toInt().coerceIn(0, 1000)
                    slim.invalidate()
                    tDur.text = fmt(d)
                }
                tPos.text = fmt(p)
            }
            ui.postDelayed(this, 500)
        }
    }

    private fun openFull() {
        if (fullOpen) return
        fullOpen = true
        val h = resources.displayMetrics.heightPixels.toFloat()
        full.translationY = h
        full.visibility = View.VISIBLE
        full.animate().translationY(0f).setDuration(480)
            .setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun closeFull() {
        if (!fullOpen) return
        fullOpen = false
        val h = resources.displayMetrics.heightPixels.toFloat()
        full.animate().translationY(h).setDuration(380)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .withEndAction { full.visibility = View.INVISIBLE }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fullOpen) closeFull() else super.onBackPressed()
    }

    // ---------- UI ----------
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Pal.BG
        window.navigationBarColor = Pal.BG
        liked = HashSet(getSharedPreferences("player", 0).getStringSet("liked", emptySet()) ?: emptySet())
        adapter = Adapter()

        val rootFrame = FrameLayout(this)
        rootFrame.setBackgroundColor(Pal.BG)

        // library page
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(22), dp(52), dp(22), 0)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(
            tv("Your library", 30f, Pal.TEXT, "sans-serif-light"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val fx = tv("Effects", 14f, Pal.MUTE, "sans-serif-medium")
        fx.background = shape(Pal.SURF, 20, Pal.LINE)
        fx.setPadding(dp(18), dp(9), dp(18), dp(9))
        fx.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        press(fx)
        head.addView(fx)
        page.addView(head)

        val search = EditText(this)
        search.hint = "Search songs"
        search.setHintTextColor(Pal.MUTE)
        search.setTextColor(Pal.TEXT)
        search.textSize = 15f
        search.isSingleLine = true
        search.background = shape(Pal.SURF, 22, Pal.LINE)
        search.setPadding(dp(18), dp(12), dp(18), dp(12))
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; refilter() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        val sp = LinearLayout.LayoutParams(-1, -2)
        sp.topMargin = dp(16)
        page.addView(search, sp)

        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        chipAll = tv("All", 14f, Pal.MUTE, "sans-serif-medium")
                chipLiked = tv("Liked", 14f, Pal.MUTE, "sans-serif-medium")
        chipPlaylists = tv("Playlists", 14f, Pal.MUTE, "sans-serif-medium")
        for (ch in listOf(chipAll, chipLiked, chipPlaylists)) {
            
            ch.setPadding(dp(18), dp(8), dp(18), dp(8))
            val l = LinearLayout.LayoutParams(-2, -2)
            l.rightMargin = dp(8)
            chips.addView(ch, l)
            press(ch)
        }
        chipAll.setOnClickListener { onlyLiked = false; paintChips(); refilter() }
        chipLiked.setOnClickListener { onlyLiked = true; paintChips(); refilter() }
        paintChips()
        val cp = LinearLayout.LayoutParams(-1, -2)
        cp.topMargin = dp(12)
        page.addView(chips, cp)

        empty = tv(
            "Koi gaana nahi mila. Music permission allow karo, aur dekho ki gaane " +
                "Music ya Downloads folder mein files (mp3, m4a) ke roop mein hain.",
            14f, Pal.MUTE
        )
        empty.visibility = View.GONE
        val ep = LinearLayout.LayoutParams(-1, -2)
        ep.topMargin = dp(24)
        page.addView(empty, ep)

        val list = ListView(this)
        list.adapter = adapter
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: AbsListView?, s: Int) {}
            override fun onScroll(v: AbsListView?, a: Int, b: Int, c: Int) {
                if (::mini.isInitialized) mini.invalidate()
            }
        })
        list.divider = null
        list.dividerHeight = 0
        list.selector = ColorDrawable(Color.TRANSPARENT)
        list.isVerticalScrollBarEnabled = false
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.clipToPadding = false
        list.setPadding(0, dp(8), 0, dp(120))
        page.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        rootFrame.addView(page, FrameLayout.LayoutParams(-1, -1))

        // mini player (glass)
        mini = GlassLayout(this, page)
        mini.orientation = LinearLayout.HORIZONTAL
        mini.gravity = Gravity.CENTER_VERTICAL
        mini.setPadding(dp(10), dp(8), dp(14), dp(8))
        mini.visibility = View.GONE
        miniImg = ImageView(this)
        miniImg.scaleType = ImageView.ScaleType.CENTER_CROP
        miniImg.background = shape(Pal.LINE, 22)
        miniImg.clipToOutline = true
        mini.addView(miniImg, LinearLayout.LayoutParams(dp(48), dp(48)))
        val mcol = LinearLayout(this)
        mcol.orientation = LinearLayout.VERTICAL
        miniTitle = tv("", 15f, Pal.TEXT, "sans-serif-medium")
        miniTitle.maxLines = 1
        miniTitle.ellipsize = TextUtils.TruncateAt.END
        miniArtist = tv("", 12f, Pal.MUTE)
        miniArtist.maxLines = 1
        miniArtist.ellipsize = TextUtils.TruncateAt.END
        mcol.addView(miniTitle)
        mcol.addView(miniArtist)
        val mcl = LinearLayout.LayoutParams(0, -2, 1f)
        mcl.leftMargin = dp(12)
        mini.addView(mcol, mcl)
        miniPlay = IconView(this, 0, Pal.TEXT)
        miniPlay.setOnClickListener {
            val c = controller
            if (c != null) { if (c.isPlaying) c.pause() else c.play() }
        }
        press(miniPlay)
        mini.addView(miniPlay, LinearLayout.LayoutParams(dp(46), dp(46)))
        val miniNext = IconView(this, 2, Pal.TEXT)
        miniNext.setOnClickListener { controller?.seekToNext() }
        press(miniNext)
        mini.addView(miniNext, LinearLayout.LayoutParams(dp(46), dp(46)))
        mini.setOnClickListener { openFull() }
        val mlp = FrameLayout.LayoutParams(-1, dp(68), Gravity.BOTTOM)
        mlp.setMargins(dp(14), 0, dp(14), dp(24))
        rootFrame.addView(mini, mlp)

        buildFull(rootFrame)
        setContentView(rootFrame)

        // permissions, then library
        val need = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            need.add("android.permission.READ_MEDIA_AUDIO")
            need.add("android.permission.POST_NOTIFICATIONS")
        } else {
            need.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) loadSongs() else requestPermissions(missing.toTypedArray(), 7)

        // connect to playback service
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateNow() }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { updateIcons() }
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateModes() }
                    override fun onRepeatModeChanged(repeatMode: Int) { updateModes() }
                })
                updateNow()
            } catch (e: Exception) {}
        }, Executor { r -> ui.post(r) })

        ui.post(ticker)

        // entrance animation
        for (i in 0 until page.childCount) {
            val v = page.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(30).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 70L).setDuration(650)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    private fun buildFull(rootFrame: FrameLayout) {
        full = LinearLayout(this)
        full.orientation = LinearLayout.VERTICAL
        full.setPadding(dp(26), dp(44), dp(26), dp(36))
        fullBg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Pal.BG, Pal.BG))
        full.background = fullBg
        full.visibility = View.INVISIBLE
        full.isClickable = true

        val handleBox = FrameLayout(this)
        val handle = View(this)
        handle.background = shape(Color.argb(110, 255, 255, 255), 3)
        handleBox.addView(handle, FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER))
        handleBox.setOnClickListener { closeFull() }
        full.addView(handleBox, LinearLayout.LayoutParams(-1, dp(30)))

        val side = resources.displayMetrics.widthPixels - dp(52)
        fullImg = ImageView(this)
        fullImg.scaleType = ImageView.ScaleType.CENTER_CROP
        fullImg.background = shape(Pal.LINE, 22)
        fullImg.clipToOutline = true
        val ip = LinearLayout.LayoutParams(side, side)
        ip.topMargin = dp(16)
        full.addView(fullImg, ip)

        val trow = LinearLayout(this)
        trow.orientation = LinearLayout.HORIZONTAL
        trow.gravity = Gravity.CENTER_VERTICAL
        val tcol = LinearLayout(this)
        tcol.orientation = LinearLayout.VERTICAL
        fullTitle = tv("", 24f, Pal.TEXT, "sans-serif-medium")
        fullTitle.maxLines = 1
        fullTitle.ellipsize = TextUtils.TruncateAt.END
        fullArtist = tv("", 16f, Pal.MUTE)
        fullArtist.maxLines = 1
        fullArtist.ellipsize = TextUtils.TruncateAt.END
        tcol.addView(fullTitle)
        tcol.addView(fullArtist)
        trow.addView(tcol, LinearLayout.LayoutParams(0, -2, 1f))
        fullStar = tv("★", 28f, Pal.MUTE)
        fullStar.setPadding(dp(12), dp(6), dp(0), dp(6))
        fullStar.setOnClickListener {
            songs.firstOrNull { it.id == currentId }?.let { toggleLike(it) }
        }
        trow.addView(fullStar)
        val trp = LinearLayout.LayoutParams(-1, -2)
        trp.topMargin = dp(26)
        full.addView(trow, trp)

        slim = Slim(this, 0, 1000) { v ->
            val c = controller
            if (c != null && c.duration > 0) c.seekTo(v * c.duration / 1000)
        }
        slim.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> seeking = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> seeking = false
            }
            false
        }
        val slp = LinearLayout.LayoutParams(-1, -2)
        slp.topMargin = dp(14)
        full.addView(slim, slp)

        val times = LinearLayout(this)
        times.orientation = LinearLayout.HORIZONTAL
        tPos = tv("0:00", 12f, Pal.MUTE)
        tDur = tv("0:00", 12f, Pal.MUTE)
        tDur.gravity = Gravity.END
        times.addView(tPos, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(tDur, LinearLayout.LayoutParams(0, -2, 1f))
        times.setPadding(dp(14), 0, dp(14), 0)
        full.addView(times, LinearLayout.LayoutParams(-1, -2))

        val ctr = LinearLayout(this)
        ctr.orientation = LinearLayout.HORIZONTAL
        ctr.gravity = Gravity.CENTER
        val prev = IconView(this, 3, Pal.TEXT)
        prev.setOnClickListener { controller?.seekToPrevious() }
        press(prev)
        ctr.addView(prev, LinearLayout.LayoutParams(dp(64), dp(64)))
        fullPlay = IconView(this, 0, Pal.BG)
        fullPlay.background = shape(Pal.TEXT, 44)
        fullPlay.setOnClickListener {
            val c = controller
            if (c != null) { if (c.isPlaying) c.pause() else c.play() }
        }
        press(fullPlay)
        val pl = LinearLayout.LayoutParams(dp(88), dp(88))
        pl.leftMargin = dp(18)
        pl.rightMargin = dp(18)
        ctr.addView(fullPlay, pl)
        val next = IconView(this, 2, Pal.TEXT)
        next.setOnClickListener { controller?.seekToNext() }
        press(next)
        ctr.addView(next, LinearLayout.LayoutParams(dp(64), dp(64)))
        val cp = LinearLayout.LayoutParams(-1, -2)
        cp.topMargin = dp(22)
        full.addView(ctr, cp)

        val modes = LinearLayout(this)
        modes.orientation = LinearLayout.HORIZONTAL
        modes.gravity = Gravity.CENTER
        chipShuffle = tv("Shuffle", 14f, Pal.TEXT, "sans-serif-medium")
        chipRepeat = tv("Repeat off", 14f, Pal.TEXT, "sans-serif-medium")
        for (ch in listOf(chipShuffle, chipRepeat)) {
            ch.setPadding(dp(20), dp(10), dp(20), dp(10))
            val l = LinearLayout.LayoutParams(-2, -2)
            l.leftMargin = dp(6)
            l.rightMargin = dp(6)
            modes.addView(ch, l)
            press(ch)
        }
        chipShuffle.setOnClickListener { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
        chipRepeat.setOnClickListener {
            controller?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        }
        val mp = LinearLayout.LayoutParams(-1, -2)
        mp.topMargin = dp(26)
        full.addView(modes, mp)

        rootFrame.addView(full, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        loadSongs()
    }

    override fun onDestroy() {
        ui.removeCallbacks(ticker)
        future?.let { MediaController.releaseFuture(it) }
        pool.shutdown()
        super.onDestroy()
    }
}
