package tv.porch.app.ui.movies

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import tv.porch.app.AppGraph
import tv.porch.app.data.Film
import tv.porch.app.data.SourceEntity
import tv.porch.app.ui.components.clickScale
import tv.porch.app.ui.theme.PorchColors
import tv.porch.app.ui.theme.PorchFocus
import tv.porch.app.ui.theme.PorchRadius
import tv.porch.app.ui.theme.PorchSpace
import tv.porch.app.ui.theme.PorchType

internal val TilePad = PorchFocus.TileOutline + PorchFocus.TileOutlineGap

/** The first Xtream source: films come only from one. */
internal suspend fun xtreamSource(graph: AppGraph): SourceEntity? =
    graph.repository.sources().firstOrNull { it.kind == "xtream" }

/** A film's poster, cropped to fill. A solid Surface until it loads; no poster or a failed load is the name tile. */
@Composable
internal fun FilmPoster(film: Film, width: Dp, height: Dp) = Poster(film.name, film.iconUrl, width, height)

/** Any poster by name and address; films and shows share it. */
@Composable
internal fun Poster(name: String, imageUrl: String?, width: Dp, height: Dp) {
    val address = imageUrl?.takeIf { it.isNotBlank() }
    if (address == null) {
        NameTile(name, width, height)
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val w = with(density) { width.roundToPx() }
    val h = with(density) { height.roundToPx() }
    val request = remember(address, w, h) {
        ImageRequest.Builder(context)
            .data(address)
            .size(Size(w, h))
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Crop)
    if (painter.state is AsyncImagePainter.State.Error) {
        NameTile(name, width, height)
        return
    }
    Box(Modifier.size(width, height).clip(RoundedCornerShape(PorchRadius.Tile)).background(PorchColors.Surface)) {
        if (painter.state is AsyncImagePainter.State.Success) {
            Image(painter, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
internal fun NameTile(name: String, width: Dp, height: Dp) {
    Box(
        Modifier.size(width, height).background(PorchColors.Surface, RoundedCornerShape(PorchRadius.Tile)).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, style = PorchType.TileTitle, color = PorchColors.Text, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** 4 tall, 2 radius, TextSecondary on a Line track. */
@Composable
internal fun ResumeBar(progress: Float, width: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(4.dp).clip(RoundedCornerShape(2.dp)).background(PorchColors.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(PorchColors.TextSecondary))
    }
}

/** A poster tile like the 24/7 one. [progress] (0 to 1) draws the resume bar under it, for Continue watching. */
@Composable
internal fun FilmTile(film: Film, progress: Float?, modifier: Modifier, onClick: () -> Unit) =
    PosterTile(film.name, film.iconUrl, progress, modifier, onClick)

/** The same tile for any name and poster address. */
@Composable
internal fun PosterTile(name: String, imageUrl: String?, progress: Float?, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PorchRadius.Tile + TilePad)
    Column(Modifier.width(PorchSpace.LoopPosterWidth + TilePad * 2)) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = PorchColors.Text,
                focusedContainerColor = PorchColors.Focus,
                focusedContentColor = PorchColors.OnFocus,
                pressedContainerColor = PorchColors.Focus,
                pressedContentColor = PorchColors.OnFocus,
            ),
            border = ClickableSurfaceDefaults.border(),
            scale = clickScale(PorchFocus.TileScale),
            glow = ClickableSurfaceDefaults.glow(),
        ) {
            // The fill holds the poster, its resume bar and the name, so focus reads as a solid light card, not an outline.
            // The name takes the surface's content colour: Text at rest, OnFocus when focused.
            Column(Modifier.padding(TilePad)) {
                Poster(name, imageUrl, PorchSpace.LoopPosterWidth, PorchSpace.LoopPosterHeight)
                Spacer(Modifier.height(TilePad))
                if (progress != null) ResumeBar(progress, PorchSpace.LoopPosterWidth)
                Spacer(Modifier.height(PorchSpace.ListGap))
                Text(name, style = PorchType.TileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
