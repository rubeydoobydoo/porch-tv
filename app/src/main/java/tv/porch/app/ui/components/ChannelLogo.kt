package tv.porch.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import tv.porch.app.domain.Channel
import tv.porch.app.domain.LogoTone
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchType

private val LogoPad = 2.dp

/**
 * A channel's badge. The letter badge is always drawn first; the logo goes on top, on a solid light plate of the
 * same shape, only once it has loaded. A missing, slow or failed logo is just the letter badge.
 */
@Composable
fun ChannelLogo(
    channel: Channel,
    color: Color,
    size: Dp = 24.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    fixedWidth: Boolean = false,
) {
    val url = channel.logoUrl
    if (url == null) {
        Badge(channel.badge, color, size, textStyle, fixedWidth)
        return
    }
    Box {
        Badge(channel.badge, color, size, textStyle, fixedWidth)
        LogoOnPlate(url, size)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.LogoOnPlate(url: String, size: Dp) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { (size - LogoPad * 2).roundToPx() }.coerceAtLeast(1)
    // Remembered per address and size, so a focus change or recomposition never starts the request again.
    // Leaving the screen (a row scrolled away) forgets the painter, which cancels a request still running.
    val request = remember(url, pixels) {
        ImageRequest.Builder(context)
            .data(url)
            .size(Size(pixels, pixels))
            .allowHardware(false) // The plate reads the logo's pixels once.
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Fit)
    if (painter.state is AsyncImagePainter.State.Success) {
        Box(
            Modifier
                .matchParentSize()
                .background(plateFor(painter), RoundedCornerShape(PorchRadius.plate(size)))
                .padding(LogoPad),
        ) {
            Image(painter, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

/**
 * A big logo tile: the logo fitted on the light plate. A channel with no logo, or one that fails to load, shows its
 * name on the plate in dark text instead, so a tile is never an empty square.
 */
@Composable
fun LogoTile(channel: Channel, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val url = channel.logoUrl
    val context = LocalContext.current
    val density = LocalDensity.current
    val w = with(density) { (width - LogoTilePad * 2).roundToPx() }.coerceAtLeast(1)
    val h = with(density) { (height - LogoTilePad * 2).roundToPx() }.coerceAtLeast(1)
    val painter = url?.let {
            val request = remember(it, w, h) {
                ImageRequest.Builder(context)
                    .data(it)
                    .size(Size(w, h))
                    .allowHardware(false) // The plate reads the logo's pixels once.
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            rememberAsyncImagePainter(model = request, contentScale = ContentScale.Fit)
    }
    Box(
        modifier
            .size(width, height)
            .background(if (painter != null) plateFor(painter) else PorchColors.LogoPlate, RoundedCornerShape(PorchRadius.Tile))
            .padding(LogoTilePad),
        contentAlignment = Alignment.Center,
    ) {
        var showName = url == null
        if (painter != null) {
            when (painter.state) {
                is AsyncImagePainter.State.Success ->
                    Image(painter, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                is AsyncImagePainter.State.Error -> showName = true
                else -> Unit
            }
        }
        if (showName) {
            Text(
                channel.shownTitle,
                style = PorchType.TileTitle,
                color = PorchColors.OnPlate,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val LogoTilePad = 8.dp

/** The light plate, or the dark one for a logo that would vanish on it. Read once per loaded logo, every other pixel. */
@Composable
private fun plateFor(painter: AsyncImagePainter): Color {
    val drawable = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable
    return remember(drawable) {
        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        val dark = bitmap != null && runCatching {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            LogoTone.needsDarkPlate(pixels)
        }.getOrDefault(false)
        if (dark) PorchColors.LogoPlateDark else PorchColors.LogoPlate
    }
}
