package tv.porch.app.launcher

import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tv.porch.app.MainActivity
import tv.porch.app.R
import tv.porch.app.data.ChannelFolds
import tv.porch.app.data.LibraryRepository
import tv.porch.app.data.SourceEntity
import tv.porch.app.domain.GroupChoice
import tv.porch.app.domain.HomeLists
import tv.porch.app.domain.LauncherRow
import tv.porch.app.domain.formatTime

/**
 * Keeps one "Porch" row on the Android TV launcher: what is on now on the viewer's channels.
 * It reads Room and writes the platform TV provider. It starts no player and no refresh.
 * A device without the provider, or a write the provider refuses, leaves the row as it was and never crashes the app.
 */
class LauncherRowSync(
    private val context: Context,
    private val repository: LibraryRepository,
    private val folds: ChannelFolds,
) {
    private val prefs = context.getSharedPreferences("porch", Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** Works out the cards and, when they differ from the last write, replaces the row. Safe to call from any thread, any time. */
    suspend fun update() {
        lock.withLock {
            guarded("update") {
                val cards = withContext(Dispatchers.Default) { pickCards() } ?: return
                withContext(Dispatchers.IO) { write(cards) }
            }
        }
    }

    private suspend fun pickCards(): List<LauncherRow.Card>? {
        val sources = repository.sources()
        if (sources.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val listing = withTimeoutOrNull(10_000L) { folds.listing.first() } ?: return null
        val chosen = repository.observeChosenGroups().first()
        val mine = GroupChoice.filter(listing.channels, GroupChoice.live(chosen, listing.channels))
        val onNow = repository.observeOnNow(now).first()
        val offsets = sources.associate { it.id to it.guideOffsetMinutes }
        val lists = HomeLists.build(mine, onNow, emptyList(), offsets, now, listing.families)
        val candidates = lists.yours.all + lists.sport.all
        val pictures = HashMap<Long, String>()
        for (id in candidates.map { it.programmeId }.filter { it > 0L }.distinct().take(PICTURE_LOOKUPS)) {
            repository.programmeDetail(id)?.iconUrl?.let { pictures[id] = it }
        }
        return LauncherRow.pick(lists.yours.all, lists.sport.all, pictures, now, listing.families, sources.associate { it.id to secretsOf(it) })
    }

    /** Text that must never ride along in a picture address: the source's login and the name of its server. */
    private fun secretsOf(source: SourceEntity): List<String> =
        listOfNotNull(source.xtreamUsername, source.xtreamPassword, hostOf(source.xtreamServer), hostOf(source.m3uUrl)).filter { it.isNotBlank() }

    private fun hostOf(address: String?): String? {
        val text = address?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            java.net.URI(if ("://" in text) text else "http://$text").host
        } catch (e: Exception) {
            null
        }
    }

    private fun write(cards: List<LauncherRow.Card>) {
        val key = LauncherRow.keyOf(cards)
        val known = prefs.getLong(CHANNEL_ID, NONE).takeIf { it != NONE && channelExists(it) } ?: findOurChannel()
        // Nothing to show and no row yet: leave the launcher alone.
        val channelId = known ?: (if (cards.isEmpty()) null else createChannel()?.also { prefs.edit().remove(LAST_KEY).apply() }) ?: return
        prefs.edit().putLong(CHANNEL_ID, channelId).apply()
        if (key == prefs.getString(LAST_KEY, null)) return
        // The old cards go first, so the launcher never shows two rows' worth.
        guarded("clear") { context.contentResolver.delete(TvContract.buildPreviewProgramsUriForChannel(channelId), null, null) }
        prefs.edit().remove(LAST_KEY).apply()
        var allWritten = true
        cards.forEachIndexed { index, card ->
            val done = guarded("card") {
                context.contentResolver.insert(TvContract.PreviewPrograms.CONTENT_URI, programValues(channelId, card, cards.size - index))
            }
            if (done == null) allWritten = false
        }
        if (allWritten) prefs.edit().putString(LAST_KEY, key).apply()
    }

    private fun programValues(channelId: Long, card: LauncherRow.Card, weight: Int) = ContentValues().apply {
        put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
        put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_CHANNEL)
        put(TvContract.PreviewPrograms.COLUMN_TITLE, card.title)
        put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, "${card.channelName} · ends ${formatTime(card.endMs)}")
        put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, card.image)
        put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_16_9)
        put(TvContract.PreviewPrograms.COLUMN_LIVE, 1)
        put(TvContract.PreviewPrograms.COLUMN_WEIGHT, weight)
        put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, cardIntent(card.channelId).toUri(Intent.URI_INTENT_SCHEME))
        put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, card.channelId.toString())
    }

    /** Opens Porch on that channel. It names our own package, so no other app can answer it. */
    private fun cardIntent(channelId: Long) =
        Intent(Intent.ACTION_VIEW, Uri.parse(LauncherRow.linkFor(channelId))).setPackage(context.packageName)

    private fun channelExists(id: Long): Boolean = guarded("check") {
        context.contentResolver.query(TvContract.buildChannelUri(id), arrayOf(TvContract.Channels._ID), null, null, null)?.use { it.moveToFirst() }
    } == true

    /** A channel we made before whose id we no longer remember (the saved id is gone). */
    private fun findOurChannel(): Long? = guarded("find") {
        context.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == PROVIDER_ID) return@use cursor.getLong(0)
            }
            null
        }
    }

    private fun createChannel(): Long? {
        val values = ContentValues().apply {
            put(TvContract.Channels.COLUMN_DISPLAY_NAME, ROW_NAME)
            put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
            put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, PROVIDER_ID)
            put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, openPorch().toUri(Intent.URI_INTENT_SCHEME))
        }
        val id = guarded("channel") {
            context.contentResolver.insert(TvContract.Channels.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
        } ?: return null
        guarded("logo") { writeLogo(id) }
        guarded("browsable") { TvContract.requestChannelBrowsable(context, id) }
        return id
    }

    private fun openPorch() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        .setComponent(ComponentName(context, MainActivity::class.java))

    /** The launcher icon, drawn to a picture, as the row's logo. */
    private fun writeLogo(channelId: Long) {
        val icon = context.getDrawable(R.mipmap.ic_launcher) ?: return
        val bitmap = Bitmap.createBitmap(LOGO_SIZE, LOGO_SIZE, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, LOGO_SIZE, LOGO_SIZE)
        icon.draw(Canvas(bitmap))
        context.contentResolver.openOutputStream(TvContract.buildChannelLogoUri(channelId))?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Runs [block]; a failure is logged by its class name alone (never its message, which could carry an address) and gives null. */
    private inline fun <T> guarded(what: String, block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Log.w(TAG, "Launcher row $what failed: ${e.javaClass.simpleName}")
        null
    }

    private companion object {
        const val TAG = "Porch"
        const val ROW_NAME = "Porch"
        const val PROVIDER_ID = "porch-main"
        const val CHANNEL_ID = "launcher_channel_id"
        const val LAST_KEY = "launcher_last_key"
        const val NONE = -1L
        const val LOGO_SIZE = 256
        const val PICTURE_LOOKUPS = 60
    }
}
