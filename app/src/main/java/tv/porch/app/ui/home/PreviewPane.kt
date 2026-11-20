package tv.porch.app.ui.home

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Text
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import tv.porch.app.domain.ProgrammeText
import tv.porch.app.domain.Channel
import tv.porch.app.domain.HomePreview
import tv.porch.app.domain.LiveRows
import tv.porch.app.domain.ProgrammeDetail
import tv.porch.app.ui.components.ChannelLogo
import tv.porch.app.ui.components.LogoCard
import tv.porch.app.ui.components.rememberNow
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

/** Focus has to rest this long before a row's description and picture are fetched, so moving quickly fetches nothing. */
private const val SETTLE_MS = 250L

/** A loaded picture, and the channel it belongs to. It is never drawn over another channel. */
private class Picture(val channelId: Long, val image: ImageBitmap)

/**
 * The top of Home: a picture, then what is on. It shows [row]; null draws the plain block alone, while the first load runs.
 * The title and logo come straight from the row, so they change at once. The description and picture wait for focus to settle.
 */
@Composable
fun PreviewPane(row: LiveRows.Row?, loadDetail: suspend (Long) -> ProgrammeDetail?, modifier: Modifier = Modifier) {
    val channel = row?.channels?.first()
    PreviewFrame(channel, row?.programmeId ?: 0L, loadDetail, modifier) { description ->
        if (row != null && channel != null) {
            val others = row.channels.size - 1
            PreviewWords(
                channel = channel,
                channelLine = channel.shownTitle + if (others > 0) " · +$others channel${if (others == 1) "" else "s"}" else "",
                title = ProgrammeText.title(row.title),
                description = description,
                descriptionLines = 3,
            ) { PreviewStatus(row) }
        }
    }
}

/**
 * The pane's frame, shared by Home and the guide: the picture for [programmeId] on [channel], then [words].
 * The description and picture wait for focus to settle, so moving quickly fetches nothing.
 * The old picture stays up until the new one is ready, so a programme changing on the same channel doesn't flash.
 * It is cleared when the channel changes, when there is no new picture, or when the new one fails.
 */
@Composable
fun PreviewFrame(
    channel: Channel?,
    programmeId: Long,
    loadDetail: suspend (Long) -> ProgrammeDetail?,
    modifier: Modifier = Modifier,
    paneHeight: Dp = PorchSpace.PreviewHeight,
    pictureWidth: Dp = PorchSpace.PreviewPictureWidth,
    words: @Composable (description: String?) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val width = with(density) { pictureWidth.roundToPx() }
    val height = with(density) { paneHeight.roundToPx() }
    val channelId = channel?.id ?: 0L
    var detail by remember { mutableStateOf<ProgrammeDetail?>(null) }
    var picture by remember { mutableStateOf<Picture?>(null) }
    // Moving on cancels this, in the wait or in the download.
    LaunchedEffect(channelId, programmeId) {
        if (programmeId == 0L) {
            detail = null
            picture = null
            return@LaunchedEffect
        }
        delay(SETTLE_MS)
        // A failed lookup (a database error) leaves the logo block; cancelling still ends this.
        val found = try {
            loadDetail(programmeId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null
        }
        detail = found
        val image = found?.iconUrl?.let { loadPicture(context, it, width, height) }
        picture = image?.let { Picture(channelId, it) }
    }
    Row(
        modifier.fillMaxWidth().height(paneHeight),
        horizontalArrangement = Arrangement.spacedBy(PorchSpace.PreviewGap),
    ) {
        PreviewPicture(channel, picture?.takeIf { it.channelId == channelId }?.image, pictureWidth, paneHeight)
        Column(Modifier.fillMaxSize()) {
            words(ProgrammeText.synopsis(detail?.takeIf { it.id == programmeId }?.description))
        }
    }
}

/** The picture, over the logo card. Slow, missing and failed all look the same. */
@Composable
private fun PreviewPicture(channel: Channel?, image: ImageBitmap?, width: Dp, height: Dp) {
    // The guide's short card has room for the logo alone.
    val compact = height < PorchSpace.PreviewHeight
    LogoCard(channel, width, height, logo = if (compact) PorchSpace.GuidePreviewLogo else PorchSpace.PreviewLogo, logoOnly = compact) {
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(PorchRadius.Tile)), contentScale = ContentScale.Crop)
        }
    }
}

/** The pane's words, 4 apart: logo and [channelLine], [title] (none while the guide loads), [status], then [description]. */
@Composable
fun PreviewWords(
    channel: Channel,
    channelLine: String,
    title: String?,
    description: String?,
    descriptionLines: Int,
    showChannelLine: Boolean = true,
    titleStyle: TextStyle = PorchType.PreviewTitle,
    titleLines: Int = 2,
    status: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PorchSpace.PreviewLineGap)) {
        if (showChannelLine) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PorchSpace.ListGap)) {
                key(channel.id) {
                    ChannelLogo(channel, channelColor(channel), PorchSpace.PreviewSmallLogo, PorchType.Meta.copy(color = Color.White), fixedWidth = true)
                }
                Text(channelLine, style = PorchType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (title != null) {
            Text(title, style = titleStyle, color = PorchColors.Text, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
        }
        status()
        if (description != null) {
            // Gets what is left, so the longest title and description still end inside the pane.
            Text(
                description,
                modifier = Modifier.weight(1f, fill = false),
                style = PorchType.Body,
                color = PorchColors.TextSecondary,
                maxLines = descriptionLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** On its own so the clock's tick redraws this one line. */
@Composable
private fun PreviewStatus(row: LiveRows.Row) {
    val status = HomePreview.status(row, rememberNow())
    PreviewStatusLine(status.live, status.text)
}

/** The status line in `Meta`, led by the word LIVE in `LiveOnDark` when [live]. */
@Composable
fun PreviewStatusLine(live: Boolean, text: String) {
    Text(
        buildAnnotatedString {
            if (live) {
                withStyle(SpanStyle(color = PorchColors.LiveOnDark, fontWeight = FontWeight.Bold)) { append("LIVE") }
                append(" · ")
            }
            append(text)
        },
        style = PorchType.Meta,
        maxLines = 1,
    )
}

/** Decoded to the size it is drawn at. Null when it fails: a dead address, a slow server, or not a picture. */
internal suspend fun loadPicture(context: Context, url: String, width: Int, height: Int): ImageBitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(Size(width, height))
        .scale(Scale.FILL)
        .precision(Precision.INEXACT)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    return (result.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
}
